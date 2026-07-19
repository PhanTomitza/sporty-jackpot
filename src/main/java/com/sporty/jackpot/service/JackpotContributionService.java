package com.sporty.jackpot.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sporty.jackpot.domain.Bet;
import com.sporty.jackpot.domain.BetRepository;
import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.JackpotContribution;
import com.sporty.jackpot.domain.JackpotContributionRepository;
import com.sporty.jackpot.domain.JackpotRepository;
import com.sporty.jackpot.messaging.BetMessage;
import com.sporty.jackpot.strategy.ContributionStrategy;
import com.sporty.jackpot.strategy.ContributionStrategyResolver;

/**
 * Applies a bet's contribution to its jackpot pool.
 *
 * <p>This is the single downstream entry point for bet processing. Both the real Kafka consumer
 * and the mock-profile publisher call it, so the two run modes exercise identical logic and the
 * mock mode is a real test of the contribution path rather than a stub of it.
 *
 * <p><b>Why the pessimistic lock is required despite Kafka keying by jackpotId.</b> Keying gives
 * per-partition ordering <em>among Kafka consumers only</em>. It says nothing about any other
 * writer. Two such writers exist: the Phase 5 evaluate endpoint mutates the same pool from an HTTP
 * request thread that never goes through Kafka, and under the {@code mock} profile there is no
 * Kafka at all — every bet is processed on the HTTP thread that posted it, so several can run
 * concurrently against one pool with no partition to serialise them. The lock, not the key, is
 * what makes the read-modify-write on the pool safe.
 */
@Service
public class JackpotContributionService {

    private static final Logger log = LoggerFactory.getLogger(JackpotContributionService.class);

    private final JackpotRepository jackpotRepository;
    private final BetRepository betRepository;
    private final JackpotContributionRepository contributionRepository;
    private final ContributionStrategyResolver contributionStrategyResolver;

    public JackpotContributionService(JackpotRepository jackpotRepository,
            BetRepository betRepository,
            JackpotContributionRepository contributionRepository,
            ContributionStrategyResolver contributionStrategyResolver) {
        this.jackpotRepository = jackpotRepository;
        this.betRepository = betRepository;
        this.contributionRepository = contributionRepository;
        this.contributionStrategyResolver = contributionStrategyResolver;
    }

    /**
     * Contributes a portion of {@code bet} to its jackpot pool.
     *
     * <p>All three writes — the bet, the contribution ledger row, and the updated pool — happen in
     * one transaction. If they were split, a crash between them could leave the ledger claiming a
     * contribution the pool never received, or a pool balance no ledger row explains. The ledger
     * must always reconcile to the pool, so they commit together or not at all.
     *
     * <p>An unknown jackpot is not an error. The spec contributes only "if there is such a
     * jackpot", so this logs a warning and returns normally; throwing would fail the Kafka listener
     * and put a permanently unprocessable message into an endless redelivery loop.
     *
     * <p><b>Idempotency: {@code betId} is the idempotency key for bet processing.</b> Kafka
     * guarantees at-least-once delivery, so the same bet legitimately arrives more than once on a
     * consumer restart, a rebalance, or any redelivery after an uncommitted offset. Without a guard
     * the second delivery would insert a second ledger row and increment the pool again for a
     * single bet. A {@code betId} already present in the bet table is therefore skipped.
     *
     * <p>The skip is decided on {@code betId} alone: <b>a second delivery of a known betId is
     * skipped regardless of whether its payload differs</b> from the first. A differing payload for
     * an existing betId means either a redelivery or a producer reusing an identifier, and neither
     * is grounds to move a pool twice. The first delivery wins and its recorded contribution stands.
     *
     * <p>The guard runs after the pessimistic lock is taken, so two concurrent deliveries of one
     * betId cannot both pass it: the second blocks on the lock and observes the first's committed
     * bet row. Note the limit of that argument — it holds because both deliveries lock the same
     * jackpot row. Two concurrent deliveries of one betId naming <em>different</em> jackpots lock
     * different rows and could both pass the check; there the bet table's primary key rejects the
     * second insert and rolls that transaction back, so the pool still moves only once, but via a
     * constraint violation rather than this clean skip.
     */
    @Transactional
    public void processBet(BetMessage bet) {
        Optional<Jackpot> found = jackpotRepository.findWithLockById(bet.jackpotId());
        if (found.isEmpty()) {
            log.warn("Bet {} references unknown jackpot '{}' — no contribution recorded, bet discarded",
                    bet.betId(), bet.jackpotId());
            return;
        }
        Jackpot jackpot = found.get();

        // Idempotency guard. Deliberately placed AFTER the lock is acquired, not at the top of the
        // method: the check only excludes a concurrent duplicate because the lock above has already
        // serialised the two deliveries. Checked before the lock, two concurrent deliveries of one
        // betId would both read "absent", then queue on the lock and both contribute — the exact
        // double-count this guard exists to prevent.
        if (betRepository.existsById(bet.betId())) {
            log.info("Bet {} has already been processed — skipping duplicate delivery for jackpot '{}'",
                    bet.betId(), bet.jackpotId());
            return;
        }

        // Resolve and calculate BEFORE mutating: the variable strategy derives its rate from the
        // current pool, so contributing first would decay the rate using this bet's own effect.
        ContributionStrategy strategy = contributionStrategyResolver.resolve(jackpot.getContributionType());
        BigDecimal contributionAmount = strategy.calculateContribution(bet.betAmount(), jackpot);

        jackpot.contribute(contributionAmount);

        Instant now = Instant.now();
        betRepository.save(Bet.builder()
                .betId(bet.betId())
                .userId(bet.userId())
                .jackpotId(bet.jackpotId())
                .betAmount(bet.betAmount())
                .createdAt(now)
                .build());

        contributionRepository.save(JackpotContribution.builder()
                .betId(bet.betId())
                .userId(bet.userId())
                .jackpotId(bet.jackpotId())
                .stakeAmount(bet.betAmount())
                .contributionAmount(contributionAmount)
                // The pool AFTER this contribution, so the row is a self-contained statement of
                // the balance at that moment and needs no replay to interpret.
                .currentJackpotAmount(jackpot.getCurrentPoolAmount())
                .createdAt(now)
                .build());

        jackpotRepository.save(jackpot);

        log.info("Bet {} contributed {} to jackpot '{}' ({} strategy); pool is now {}",
                bet.betId(), contributionAmount, jackpot.getId(),
                jackpot.getContributionType(), jackpot.getCurrentPoolAmount());
    }
}

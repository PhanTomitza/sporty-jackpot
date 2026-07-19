package com.sporty.jackpot.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sporty.jackpot.config.RandomProvider;
import com.sporty.jackpot.domain.Bet;
import com.sporty.jackpot.domain.BetRepository;
import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.JackpotRepository;
import com.sporty.jackpot.domain.JackpotReward;
import com.sporty.jackpot.domain.JackpotRewardRepository;
import com.sporty.jackpot.exception.BetNotFoundException;
import com.sporty.jackpot.strategy.RewardStrategy;
import com.sporty.jackpot.strategy.RewardStrategyResolver;

/**
 * Decides whether a processed bet wins its jackpot, and pays out if it does.
 *
 * <p>On a win the reward is the <em>whole</em> current pool and the pool returns to its initial
 * value, per the spec: "When a jackpot is rewarded, it should be reset to the initial pool value."
 */
@Service
public class RewardEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RewardEvaluationService.class);

    private final BetRepository betRepository;
    private final JackpotRepository jackpotRepository;
    private final JackpotRewardRepository jackpotRewardRepository;
    private final RewardStrategyResolver rewardStrategyResolver;
    private final RandomProvider randomProvider;

    public RewardEvaluationService(BetRepository betRepository,
            JackpotRepository jackpotRepository,
            JackpotRewardRepository jackpotRewardRepository,
            RewardStrategyResolver rewardStrategyResolver,
            RandomProvider randomProvider) {
        this.betRepository = betRepository;
        this.jackpotRepository = jackpotRepository;
        this.jackpotRewardRepository = jackpotRewardRepository;
        this.rewardStrategyResolver = rewardStrategyResolver;
        this.randomProvider = randomProvider;
    }

    /**
     * Evaluates {@code betId} against its jackpot.
     *
     * <p><b>The pessimistic lock is taken first, before any idempotency check.</b> It is the same
     * lock the contribution path takes, for the same reason — this method performs a
     * read-modify-write on the pool from an HTTP request thread that never passes through Kafka, so
     * partition ordering does not serialise it against a concurrent contribution.
     *
     * <p>But the ordering matters independently of that. <b>The existing-reward read must happen
     * under the lock, not before it.</b> Checked first, two concurrent evaluations of one bet would
     * both read "no reward", both proceed to roll, and both pay out — the second against the pool
     * the first had just reset, so it would pay the seed value out of a jackpot that had already
     * been emptied. Locking first means the loser of the race blocks until the winner commits, then
     * re-reads the reward row, sees the win, and returns the stored result. This is the same
     * argument as the contribution path's idempotency guard, which is likewise placed after its
     * lock: <em>a check only excludes a concurrent duplicate if the lock has already serialised
     * the two callers.</em>
     *
     * <p><b>Idempotency.</b> A bet that has already won returns its stored reward without rolling
     * again and without touching the pool. The evaluate call is a mutation, and callers retry
     * mutations — on a timeout, a dropped connection, a double-clicked button. Re-rolling would
     * either pay a second reward out of a pool that has since been reset, or silently turn a
     * recorded win into a loss. The stored {@link JackpotReward} is the record of record, so the
     * answer for a given bet never changes once it exists.
     *
     * <p><b>One bet, one evaluation.</b> A bet that has already been evaluated and did not win
     * returns a loss without rolling again. Without this, a losing bet stays re-rollable forever
     * and a client can simply loop the endpoint until it wins, converting one stake into unlimited
     * independent chances at the pool. The spec asks whether <em>a bet</em> wins the jackpot — that
     * is one question with one answer, not a draw the caller may repeat. {@code evaluatedAt} is
     * therefore stamped on both the win and the loss path.
     *
     * @throws BetNotFoundException if no processed bet has this id
     */
    @Transactional
    public RewardResult evaluate(String betId) {
        Bet bet = betRepository.findById(betId)
                .orElseThrow(() -> new BetNotFoundException(betId));

        // The bet row is only written after a successful contribution, so its jackpot existed at
        // that moment. A missing one now means a jackpot was deleted underneath a live bet — a
        // broken invariant, not a routine "no such jackpot", so it fails loudly rather than
        // reporting a loss for a jackpot nobody can evaluate against.
        //
        // Taken BEFORE the two guards below, which is what makes them effective against concurrent
        // callers rather than merely against sequential retries.
        Jackpot jackpot = jackpotRepository.findWithLockById(bet.getJackpotId())
                .orElseThrow(() -> new IllegalStateException(
                        "Bet " + betId + " references jackpot '" + bet.getJackpotId()
                                + "', which no longer exists"));

        Optional<JackpotReward> existingReward = jackpotRewardRepository.findByBetId(betId);
        if (existingReward.isPresent()) {
            JackpotReward reward = existingReward.get();
            log.info("Bet {} was already rewarded {} from jackpot '{}' — returning the stored reward, pool untouched",
                    betId, reward.getJackpotRewardAmount(), reward.getJackpotId());
            return RewardResult.won(betId, reward.getJackpotRewardAmount());
        }

        // Evaluated before, and no reward row exists — so it was evaluated and lost. Checked after
        // the reward lookup, never before it: a winning bet is also marked evaluated, and reading
        // this flag first would report every past winner as a loser.
        if (bet.getEvaluatedAt() != null) {
            log.debug("Bet {} was already evaluated at {} and did not win — returning the recorded loss, no new roll",
                    betId, bet.getEvaluatedAt());
            return RewardResult.lost(betId);
        }

        RewardStrategy strategy = rewardStrategyResolver.resolve(jackpot.getRewardType());
        BigDecimal winChance = strategy.winChance(jackpot);

        // doubleValue() on the probability is deliberate and is NOT a violation of the BigDecimal
        // rule. That rule governs money, where a fraction of a cent is a real accounting error.
        // A win chance is not money: it is compared once against an RNG that produces a double
        // natively, and the comparison is thrown away. Converting the double up to BigDecimal
        // instead would add precision the RNG never had, to make a decision no amount depends on.
        // The reward amount itself stays BigDecimal end to end.
        double roll = randomProvider.nextDouble();
        Instant now = Instant.now();

        if (roll >= winChance.doubleValue()) {
            // Stamped on the loss path too — this is what spends the bet's single roll. Omitted
            // here, the guard above would never fire for a loser and the endpoint would stay
            // loopable, which is the whole point of the field.
            bet.markEvaluated(now);
            betRepository.save(bet);

            log.debug("Bet {} did not win jackpot '{}' (roll {} >= chance {}); pool stays at {}",
                    betId, jackpot.getId(), roll, winChance, jackpot.getCurrentPoolAmount());
            return RewardResult.lost(betId);
        }

        // Capture the pool BEFORE the reset — this value IS the reward, and resetting first would
        // pay out the initial amount instead of what actually accumulated.
        BigDecimal rewardAmount = jackpot.getCurrentPoolAmount();

        jackpotRewardRepository.save(JackpotReward.builder()
                .betId(betId)
                .userId(bet.getUserId())
                .jackpotId(bet.getJackpotId())
                .jackpotRewardAmount(rewardAmount)
                .createdAt(now)
                .build());

        bet.markEvaluated(now);
        betRepository.save(bet);

        // Same transaction as the reward row above. Split apart, a crash between them leaves either
        // a payout recorded against a pool that still holds the money, or a drained pool no reward
        // row explains. The payout and the reset are one fact and commit as one.
        jackpot.resetPool();
        jackpotRepository.save(jackpot);

        log.info("Bet {} WON {} from jackpot '{}' ({} reward, chance {}); pool reset to {}",
                betId, rewardAmount, jackpot.getId(), jackpot.getRewardType(), winChance,
                jackpot.getCurrentPoolAmount());

        return RewardResult.won(betId, rewardAmount);
    }
}

package com.sporty.jackpot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sporty.jackpot.config.RandomProvider;
import com.sporty.jackpot.domain.Bet;
import com.sporty.jackpot.domain.BetRepository;
import com.sporty.jackpot.domain.ContributionType;
import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.JackpotRepository;
import com.sporty.jackpot.domain.JackpotReward;
import com.sporty.jackpot.domain.JackpotRewardRepository;
import com.sporty.jackpot.domain.RewardType;
import com.sporty.jackpot.exception.BetNotFoundException;
import com.sporty.jackpot.strategy.RewardStrategy;
import com.sporty.jackpot.strategy.RewardStrategyResolver;

/**
 * Unit tests for {@link RewardEvaluationService} with every collaborator mocked.
 *
 * <p>The point of the mocked {@link RandomProvider} is that win and lose stop being probabilistic:
 * a stubbed roll makes each branch reachable on demand, so both are asserted exactly rather than
 * one being covered by luck.
 */
@ExtendWith(MockitoExtension.class)
class RewardEvaluationServiceTest {

    private static final String BET_ID = "bet-1";
    private static final String JACKPOT_ID = "test-jackpot";

    @Mock
    private BetRepository betRepository;

    @Mock
    private JackpotRepository jackpotRepository;

    @Mock
    private JackpotRewardRepository jackpotRewardRepository;

    @Mock
    private RewardStrategyResolver rewardStrategyResolver;

    @Mock
    private RewardStrategy rewardStrategy;

    @Mock
    private RandomProvider randomProvider;

    private RewardEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new RewardEvaluationService(betRepository, jackpotRepository,
                jackpotRewardRepository, rewardStrategyResolver, randomProvider);
    }

    @Test
    void winPersistsRewardEqualToThePoolBeforeResetAndResetsThePool() {
        Bet bet = bet();
        Jackpot jackpot = jackpot("1500.00", "1000.00");
        givenBetAndJackpot(bet, jackpot);
        when(jackpotRewardRepository.findByBetId(BET_ID)).thenReturn(Optional.empty());
        givenWinChance("0.10");
        // Strictly below the chance, so this is a win.
        when(randomProvider.nextDouble()).thenReturn(0.05);

        RewardResult result = service.evaluate(BET_ID);

        assertThat(result.betId()).isEqualTo(BET_ID);
        assertThat(result.won()).isTrue();
        assertThat(result.rewardAmount()).isEqualByComparingTo("1500.00");

        ArgumentCaptor<JackpotReward> captor = ArgumentCaptor.forClass(JackpotReward.class);
        verify(jackpotRewardRepository).save(captor.capture());
        JackpotReward saved = captor.getValue();
        // 1500.00, not 1000.00: the reward is the pool as it stood BEFORE the reset. Capturing it
        // after would pay out the seed value and quietly swallow everything that accumulated.
        assertThat(saved.getJackpotRewardAmount()).isEqualByComparingTo("1500.00");
        assertThat(saved.getBetId()).isEqualTo(BET_ID);
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getJackpotId()).isEqualTo(JACKPOT_ID);
        assertThat(saved.getCreatedAt()).isNotNull();

        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("1000.00");
        verify(jackpotRepository).save(jackpot);

        // Stamped on the win path too, so the bet is spent and cannot be re-evaluated.
        assertThat(bet.getEvaluatedAt()).isNotNull();
        verify(betRepository).save(bet);
    }

    @Test
    void lossPersistsNoRewardAndLeavesThePoolUntouchedButMarksTheBetEvaluated() {
        Jackpot jackpot = jackpot("1500.00", "1000.00");
        Bet bet = bet();
        givenBetAndJackpot(bet, jackpot);
        when(jackpotRewardRepository.findByBetId(BET_ID)).thenReturn(Optional.empty());
        givenWinChance("0.10");
        // At or above the chance is a loss. 0.10 itself is the boundary: the comparison is strict,
        // so a roll exactly equal to the chance must NOT win — otherwise a chance of 0 could win.
        when(randomProvider.nextDouble()).thenReturn(0.10);

        RewardResult result = service.evaluate(BET_ID);

        assertThat(result.won()).isFalse();
        assertThat(result.rewardAmount()).isNull();

        verify(jackpotRewardRepository, never()).save(any());
        verify(jackpotRepository, never()).save(any());
        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("1500.00");

        // Nothing about the POOL changed, but the bet is spent: this is what stops the caller
        // re-rolling the same bet until it wins.
        assertThat(bet.getEvaluatedAt()).isNotNull();
        verify(betRepository).save(bet);
    }

    @Test
    void alreadyRewardedBetReturnsTheStoredRewardWithoutRollingOrTouchingThePool() {
        Jackpot jackpot = jackpot("1000.00", "1000.00");
        // The jackpot IS loaded now — under its lock — because the reward lookup has to happen
        // inside that lock to exclude a concurrent second payout. It is loaded, not mutated.
        givenBetAndJackpot(betEvaluatedAt(Instant.now()), jackpot);
        when(jackpotRewardRepository.findByBetId(BET_ID)).thenReturn(Optional.of(JackpotReward.builder()
                .betId(BET_ID)
                .userId("user-1")
                .jackpotId(JACKPOT_ID)
                .jackpotRewardAmount(new BigDecimal("2500.00"))
                .createdAt(Instant.now())
                .build()));

        RewardResult result = service.evaluate(BET_ID);

        assertThat(result.won()).isTrue();
        assertThat(result.rewardAmount()).isEqualByComparingTo("2500.00");

        // The heart of the double-payout guard: no roll happens at all. If the service re-rolled
        // and merely discarded the result, a future refactor could start honouring that roll and
        // turn a recorded win into a loss — so the assertion is that it never rolls, not that the
        // answer happens to match.
        verifyNoInteractions(randomProvider);
        verifyNoInteractions(rewardStrategyResolver);
        verify(jackpotRewardRepository, never()).save(any());
        // Loaded but never written, and the pool still holds its value: no second payout, no reset.
        verify(jackpotRepository, never()).save(any());
        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void theRewardLookupHappensAfterTheLockIsAcquired() {
        Jackpot jackpot = jackpot("1500.00", "1000.00");
        givenBetAndJackpot(bet(), jackpot);
        when(jackpotRewardRepository.findByBetId(BET_ID)).thenReturn(Optional.empty());
        givenWinChance("0.10");
        when(randomProvider.nextDouble()).thenReturn(0.05);

        service.evaluate(BET_ID);

        // Ordering, not just occurrence. Read before the lock, two concurrent evaluations of one
        // bet would both see "no reward" and both pay out — the second against the pool the first
        // had already reset. Only the lock makes the check exclusive, so its position is the
        // correctness property and is asserted directly rather than left to the comments.
        InOrder inOrder = inOrder(jackpotRepository, jackpotRewardRepository, randomProvider);
        inOrder.verify(jackpotRepository).findWithLockById(JACKPOT_ID);
        inOrder.verify(jackpotRewardRepository).findByBetId(BET_ID);
        inOrder.verify(randomProvider).nextDouble();
    }

    @Test
    void alreadyEvaluatedLosingBetReturnsLostWithoutRollingAgain() {
        Jackpot jackpot = jackpot("1500.00", "1000.00");
        givenBetAndJackpot(betEvaluatedAt(Instant.now()), jackpot);
        // Evaluated before, but no reward row — so it was evaluated and lost.
        when(jackpotRewardRepository.findByBetId(BET_ID)).thenReturn(Optional.empty());

        RewardResult result = service.evaluate(BET_ID);

        assertThat(result.won()).isFalse();
        assertThat(result.rewardAmount()).isNull();

        // One bet, one roll. Without this the endpoint is a loop a client can run until it wins,
        // turning a single stake into unlimited chances at the pool.
        verifyNoInteractions(randomProvider);
        verifyNoInteractions(rewardStrategyResolver);
        verify(jackpotRewardRepository, never()).save(any());
        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("1500.00");
    }

    @Test
    void unknownBetIdThrowsBetNotFoundException() {
        when(betRepository.findById("no-such-bet")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluate("no-such-bet"))
                .isInstanceOf(BetNotFoundException.class)
                .hasMessageContaining("no-such-bet");

        verifyNoInteractions(randomProvider, jackpotRepository, rewardStrategyResolver);
    }

    // --- fixtures --------------------------------------------------------------------------

    private void givenBetAndJackpot(Bet bet, Jackpot jackpot) {
        when(betRepository.findById(BET_ID)).thenReturn(Optional.of(bet));
        when(jackpotRepository.findWithLockById(JACKPOT_ID)).thenReturn(Optional.of(jackpot));
    }

    private void givenWinChance(String chance) {
        when(rewardStrategyResolver.resolve(RewardType.FIXED)).thenReturn(rewardStrategy);
        when(rewardStrategy.winChance(any(Jackpot.class))).thenReturn(new BigDecimal(chance));
    }

    /** A bet that has never been evaluated. */
    private Bet bet() {
        return betEvaluatedAt(null);
    }

    private Bet betEvaluatedAt(Instant evaluatedAt) {
        return Bet.builder()
                .betId(BET_ID)
                .userId("user-1")
                .jackpotId(JACKPOT_ID)
                .betAmount(new BigDecimal("100.00"))
                .createdAt(Instant.now())
                .evaluatedAt(evaluatedAt)
                .build();
    }

    private Jackpot jackpot(String currentPool, String initialPool) {
        return Jackpot.builder()
                .id(JACKPOT_ID)
                .currentPoolAmount(new BigDecimal(currentPool))
                .initialPoolAmount(new BigDecimal(initialPool))
                .contributionType(ContributionType.FIXED)
                .contributionRate(new BigDecimal("0.05"))
                .rewardType(RewardType.FIXED)
                .rewardChance(new BigDecimal("0.10"))
                .build();
    }
}

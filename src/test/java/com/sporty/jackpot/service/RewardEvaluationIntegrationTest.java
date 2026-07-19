package com.sporty.jackpot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sporty.jackpot.config.RandomProvider;
import com.sporty.jackpot.domain.BetRepository;
import com.sporty.jackpot.domain.ContributionType;
import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.JackpotContributionRepository;
import com.sporty.jackpot.domain.JackpotRepository;
import com.sporty.jackpot.domain.JackpotReward;
import com.sporty.jackpot.domain.JackpotRewardRepository;
import com.sporty.jackpot.domain.RewardType;

/**
 * End-to-end test of the evaluation path: HTTP POST → contribution → evaluate → database, under the
 * {@code mock} profile where publishing contributes synchronously.
 *
 * <p><b>The RNG is overridden by a stub via {@link TestConfiguration}.</b> With the real
 * {@code ThreadLocalRandomProvider} these tests would win only sometimes — the win path would be
 * asserted by a coin flip, which is a flaky test rather than a test of the win path. The stub makes
 * the outcome an input, so each test states which branch it is exercising.
 *
 * <p>As in Phase 4, each test works against its own jackpot id rather than a seeded one, so the
 * tests stay independent of {@code application.yml} and of each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class RewardEvaluationIntegrationTest {

    private static final String FIXED_JACKPOT = "eval-fixed-jackpot";
    private static final String VARIABLE_JACKPOT = "eval-variable-jackpot";

    /**
     * Replaces the production RNG for this context. {@code @Primary} rather than bean-definition
     * overriding: it wins the injection point without loosening a context-wide safety setting.
     */
    @TestConfiguration
    static class StubRandomConfig {

        @Bean
        @Primary
        StubRandomProvider stubRandomProvider() {
            return new StubRandomProvider();
        }
    }

    /** A RandomProvider whose next value is dictated by the test. */
    static class StubRandomProvider implements RandomProvider {

        private volatile double value;

        void willReturn(double value) {
            this.value = value;
        }

        @Override
        public double nextDouble() {
            return value;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JackpotRepository jackpotRepository;

    @Autowired
    private BetRepository betRepository;

    @Autowired
    private JackpotContributionRepository contributionRepository;

    @Autowired
    private JackpotRewardRepository rewardRepository;

    @Autowired
    private StubRandomProvider randomProvider;

    @BeforeEach
    void setUp() {
        // The context and its H2 schema are shared across the class, so rows from a previous test
        // would otherwise leak into these assertions.
        rewardRepository.deleteAll();
        contributionRepository.deleteAll();
        betRepository.deleteAll();

        jackpotRepository.save(Jackpot.builder()
                .id(FIXED_JACKPOT)
                .initialPoolAmount(new BigDecimal("1000.00"))
                .currentPoolAmount(new BigDecimal("1000.00"))
                .contributionType(ContributionType.FIXED)
                .contributionRate(new BigDecimal("0.05"))
                .rewardType(RewardType.FIXED)
                .rewardChance(new BigDecimal("0.10"))
                .build());

        // Deliberately seeded 10.00 short of its reward limit: a single 200.00 bet contributes
        // 10.00 and pushes the pool exactly onto the limit, so the guaranteed-win rule is triggered
        // by the service's own contribution rather than by a hand-written pool value.
        jackpotRepository.save(Jackpot.builder()
                .id(VARIABLE_JACKPOT)
                .initialPoolAmount(new BigDecimal("1000.00"))
                .currentPoolAmount(new BigDecimal("1990.00"))
                .contributionType(ContributionType.FIXED)
                .contributionRate(new BigDecimal("0.05"))
                .rewardType(RewardType.VARIABLE)
                .rewardChance(new BigDecimal("0.01"))
                .rewardPoolLimit(new BigDecimal("2000.00"))
                .build());
    }

    @Test
    void winPaysOutThePostContributionPoolAndResetsTheJackpot() throws Exception {
        randomProvider.willReturn(0.0); // below any positive chance — a guaranteed win
        postBet("bet-win-1", "user-1", FIXED_JACKPOT, "100.00");

        // 100.00 * 0.05 = 5.00, so the pool is 1005.00 when the evaluation runs.
        assertThat(poolOf(FIXED_JACKPOT)).isEqualByComparingTo("1005.00");

        evaluate("bet-win-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.betId").value("bet-win-1"))
                .andExpect(jsonPath("$.won").value(true))
                .andExpect(jsonPath("$.rewardAmount").value(1005.00));

        List<JackpotReward> rewards = rewardRepository.findAll();
        assertThat(rewards).hasSize(1);
        JackpotReward reward = rewards.get(0);
        // The reward is the pool INCLUDING this bet's own contribution, not the pool before it.
        assertThat(reward.getJackpotRewardAmount()).isEqualByComparingTo("1005.00");
        assertThat(reward.getUserId()).isEqualTo("user-1");
        assertThat(reward.getJackpotId()).isEqualTo(FIXED_JACKPOT);
        assertThat(reward.getCreatedAt()).isNotNull();

        // "When a jackpot is rewarded, it should be reset to the initial pool value."
        assertThat(poolOf(FIXED_JACKPOT)).isEqualByComparingTo("1000.00");
    }

    @Test
    void reEvaluatingAWinningBetReturnsTheSameRewardAndDoesNotMoveThePoolAgain() throws Exception {
        randomProvider.willReturn(0.0);
        postBet("bet-win-2", "user-1", FIXED_JACKPOT, "100.00");

        evaluate("bet-win-2").andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardAmount").value(1005.00));
        assertThat(poolOf(FIXED_JACKPOT)).isEqualByComparingTo("1000.00");

        // The second call is what a retrying client sends. It must be a read of the recorded win,
        // not a fresh evaluation: a re-roll here would pay out a second reward — and at this point
        // the pool holds only the seed value, so it would also pay out the WRONG amount.
        evaluate("bet-win-2").andExpect(status().isOk())
                .andExpect(jsonPath("$.won").value(true))
                .andExpect(jsonPath("$.rewardAmount").value(1005.00));

        assertThat(rewardRepository.findAll()).hasSize(1);
        assertThat(poolOf(FIXED_JACKPOT)).isEqualByComparingTo("1000.00");
    }

    @Test
    void variableJackpotAtItsRewardPoolLimitWinsRegardlessOfTheRoll() throws Exception {
        // Far above the 0.01 base chance: without the guaranteed-win rule this roll loses.
        randomProvider.willReturn(0.999999);
        postBet("bet-limit-1", "user-1", VARIABLE_JACKPOT, "200.00");

        // 1990.00 + (200.00 * 0.05) = 2000.00, exactly the rewardPoolLimit — where the variable
        // strategy's win chance is 1.0.
        assertThat(poolOf(VARIABLE_JACKPOT)).isEqualByComparingTo("2000.00");

        evaluate("bet-limit-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.won").value(true))
                .andExpect(jsonPath("$.rewardAmount").value(2000.00));

        assertThat(rewardRepository.findAll()).hasSize(1);
        assertThat(poolOf(VARIABLE_JACKPOT)).isEqualByComparingTo("1000.00");
    }

    @Test
    void lossLeavesThePoolAndTheRewardTableUntouchedButMarksTheBetEvaluated() throws Exception {
        randomProvider.willReturn(0.999999); // above the 0.10 fixed chance
        postBet("bet-lose-1", "user-1", FIXED_JACKPOT, "100.00");

        evaluate("bet-lose-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.won").value(false))
                .andExpect(jsonPath("$.rewardAmount").doesNotExist());

        assertThat(rewardRepository.findAll()).isEmpty();
        // The contribution stands; only the evaluation was a no-op for the pool.
        assertThat(poolOf(FIXED_JACKPOT)).isEqualByComparingTo("1005.00");
        assertThat(evaluatedAtOf("bet-lose-1")).isNotNull();
    }

    @Test
    void aLosingBetCannotBeReRolledEvenWhenTheNextRollWouldWin() throws Exception {
        randomProvider.willReturn(0.999999); // loses against the 0.10 chance
        postBet("bet-lose-2", "user-1", FIXED_JACKPOT, "100.00");

        evaluate("bet-lose-2").andExpect(jsonPath("$.won").value(false));

        // The attack this closes: a client that loses simply calls again. The stub is now set to a
        // roll that WOULD win, so if the service re-rolled, this second call would pay out — the
        // assertion below is therefore a real test of the guard, not of the stub still losing.
        randomProvider.willReturn(0.0);
        evaluate("bet-lose-2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.won").value(false))
                .andExpect(jsonPath("$.rewardAmount").doesNotExist());

        assertThat(rewardRepository.findAll()).isEmpty();
        assertThat(poolOf(FIXED_JACKPOT)).isEqualByComparingTo("1005.00");
    }

    @Test
    void winningBetIsAlsoMarkedEvaluated() throws Exception {
        randomProvider.willReturn(0.0);
        postBet("bet-win-3", "user-1", FIXED_JACKPOT, "100.00");

        evaluate("bet-win-3").andExpect(jsonPath("$.won").value(true));

        assertThat(evaluatedAtOf("bet-win-3")).isNotNull();
    }

    @Test
    void theDatabaseRejectsASecondRewardRowForOneBet() {
        // Defence in depth behind the lock: bypass the service entirely and write a duplicate
        // reward directly, which is what a stray code path or a manual fix would do. The unique
        // constraint is what turns "the service does not do this" into "this cannot happen".
        rewardRepository.save(reward("bet-constraint-1"));

        assertThatThrownBy(() -> rewardRepository.saveAndFlush(reward("bet-constraint-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private JackpotReward reward(String betId) {
        return JackpotReward.builder()
                .betId(betId)
                .userId("user-1")
                .jackpotId(FIXED_JACKPOT)
                .jackpotRewardAmount(new BigDecimal("1000.00"))
                .createdAt(Instant.now())
                .build();
    }

    private Instant evaluatedAtOf(String betId) {
        return betRepository.findById(betId).orElseThrow().getEvaluatedAt();
    }

    @Test
    void evaluatingAnUnknownBetIdReturns404() throws Exception {
        evaluate("no-such-bet")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BET_NOT_FOUND"));

        assertThat(rewardRepository.findAll()).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions evaluate(String betId) throws Exception {
        return mockMvc.perform(post("/api/bets/{betId}/evaluate", betId));
    }

    private void postBet(String betId, String userId, String jackpotId, String amount) throws Exception {
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "betId": "%s",
                                  "userId": "%s",
                                  "jackpotId": "%s",
                                  "betAmount": %s
                                }
                                """.formatted(betId, userId, jackpotId, amount)))
                .andExpect(status().isAccepted());
    }

    private BigDecimal poolOf(String jackpotId) {
        return jackpotRepository.findById(jackpotId).orElseThrow().getCurrentPoolAmount();
    }
}

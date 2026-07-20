package com.sporty.jackpot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sporty.jackpot.domain.BetRepository;
import com.sporty.jackpot.domain.ContributionType;
import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.JackpotContribution;
import com.sporty.jackpot.domain.JackpotContributionRepository;
import com.sporty.jackpot.domain.JackpotRepository;
import com.sporty.jackpot.domain.RewardType;

/**
 * End-to-end test of the contribution path: HTTP POST → publisher → contribution service →
 * database. Runs under the {@code mock} profile, where {@code LoggingBetPublisher} calls the
 * service synchronously, so every assertion below observes committed state with no waiting and no
 * broker.
 *
 * <p><b>Each test inserts its own jackpot with a unique id in {@link BeforeEach} rather than
 * reusing any of the four seeded jackpots.</b> Depending on seeded rows would couple these tests
 * to {@code application.yml} (a changed seed rate silently breaks an unrelated assertion) and to
 * each other (contributions accumulate in the pool, so a second test running against the same
 * jackpot would see a pool the first test moved). Own fixture per test means order-independent,
 * config-independent tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class JackpotContributionIntegrationTest {

    private static final String FIXED_JACKPOT = "test-fixed-jackpot";
    private static final String VARIABLE_JACKPOT = "test-variable-jackpot";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JackpotRepository jackpotRepository;

    @Autowired
    private BetRepository betRepository;

    @Autowired
    private JackpotContributionRepository contributionRepository;

    @BeforeEach
    void setUp() {
        // The application context (and its H2 schema) is shared across the whole test class, so
        // ledger rows from a previous test would otherwise leak into these assertions.
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

        jackpotRepository.save(Jackpot.builder()
                .id(VARIABLE_JACKPOT)
                .initialPoolAmount(new BigDecimal("1000.00"))
                .currentPoolAmount(new BigDecimal("1000.00"))
                .contributionType(ContributionType.VARIABLE)
                .contributionRate(new BigDecimal("0.10"))
                .minContributionRate(new BigDecimal("0.02"))
                .contributionDecayFactor(new BigDecimal("0.05"))
                .rewardType(RewardType.FIXED)
                .rewardChance(new BigDecimal("0.10"))
                .build());
    }

    @Test
    void fixedContributionAddsFlatRateToPoolAndRecordsLedgerRow() throws Exception {
        postBet("bet-fixed-1", "user-1", FIXED_JACKPOT, "100.00");

        // 100.00 * 0.05 = 5.00
        JackpotContribution contribution = singleContribution();
        assertThat(contribution.getBetId()).isEqualTo("bet-fixed-1");
        assertThat(contribution.getJackpotId()).isEqualTo(FIXED_JACKPOT);
        assertThat(contribution.getStakeAmount()).isEqualByComparingTo("100.00");
        assertThat(contribution.getContributionAmount()).isEqualByComparingTo("5.00");
        // The ledger records the pool AFTER the contribution, not before.
        assertThat(contribution.getCurrentJackpotAmount()).isEqualByComparingTo("1005.00");

        assertThat(betRepository.findById("bet-fixed-1")).isPresent().get()
                .satisfies(bet -> {
                    assertThat(bet.getUserId()).isEqualTo("user-1");
                    assertThat(bet.getBetAmount()).isEqualByComparingTo("100.00");
                    assertThat(bet.getCreatedAt()).isNotNull();
                });

        assertThat(poolOf(FIXED_JACKPOT)).isEqualByComparingTo("1005.00");
    }

    @Test
    void variableContributionUsesStartingRateWhilePoolIsAtItsInitialValue() throws Exception {
        postBet("bet-var-1", "user-1", VARIABLE_JACKPOT, "100.00");

        // Pool is still at its initial value, so growth ratio is 0 and no decay has applied yet:
        // 100.00 * 0.10 = 10.00
        assertThat(singleContribution().getContributionAmount()).isEqualByComparingTo("10.00");
        assertThat(poolOf(VARIABLE_JACKPOT)).isEqualByComparingTo("1010.00");
    }

    @Test
    void secondBetOnVariableJackpotContributesStrictlyLessAsTheRateDecays() throws Exception {
        postBet("bet-var-1", "user-1", VARIABLE_JACKPOT, "100.00");
        postBet("bet-var-2", "user-2", VARIABLE_JACKPOT, "100.00");

        List<JackpotContribution> contributions = contributionRepository.findAll().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .toList();
        assertThat(contributions).hasSize(2);

        BigDecimal first = contributions.get(0).getContributionAmount();
        BigDecimal second = contributions.get(1).getContributionAmount();

        // The pool grew by the first contribution, so the rate has decayed for the second. This is
        // the assertion that actually proves decay: identical bets, different contributions.
        assertThat(second).isLessThan(first);
    }

    @Test
    void redeliveryOfTheSameBetIdContributesOnlyOnce() throws Exception {
        postBet("bet-dup-1", "user-1", FIXED_JACKPOT, "100.00");
        // Kafka is at-least-once, so this is a legitimate second delivery of one bet, not a client
        // error — hence still a 202, and still exactly one contribution.
        postBet("bet-dup-1", "user-1", FIXED_JACKPOT, "100.00");

        assertThat(contributionRepository.findAll()).hasSize(1);
        assertThat(singleContribution().getContributionAmount()).isEqualByComparingTo("5.00");
        // 1005.00, not 1010.00: the pool moved once for one bet.
        assertThat(poolOf(FIXED_JACKPOT)).isEqualByComparingTo("1005.00");
    }

    @Test
    void redeliveredBetIdIsSkippedEvenWhenItsPayloadDiffers() throws Exception {
        postBet("bet-dup-2", "user-1", FIXED_JACKPOT, "100.00");
        // Same betId, different stake. betId is the idempotency key on its own, so this is skipped
        // rather than contributing a second, larger amount.
        postBet("bet-dup-2", "user-1", FIXED_JACKPOT, "900.00");

        assertThat(contributionRepository.findAll()).hasSize(1);
        // The FIRST delivery's amount stands: 100.00 * 0.05, not 900.00 * 0.05.
        assertThat(singleContribution().getContributionAmount()).isEqualByComparingTo("5.00");
        assertThat(betRepository.findById("bet-dup-2")).isPresent().get()
                .satisfies(bet -> assertThat(bet.getBetAmount()).isEqualByComparingTo("100.00"));
        assertThat(poolOf(FIXED_JACKPOT)).isEqualByComparingTo("1005.00");
    }

    @Test
    void betForUnknownJackpotIsAcceptedButContributesNothingAndPersistsNothing() throws Exception {
        postBet("bet-orphan-1", "user-1", "no-such-jackpot", "100.00");

        // The spec contributes only "if there is such a jackpot" — so this is a no-op, not an
        // error. The 202 still stands: the API accepted the bet for processing, and processing it
        // is exactly what revealed there was nothing to contribute to.
        assertThat(contributionRepository.findAll()).isEmpty();
        assertThat(betRepository.findById("bet-orphan-1")).isEmpty();
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

    private JackpotContribution singleContribution() {
        List<JackpotContribution> all = contributionRepository.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    private BigDecimal poolOf(String jackpotId) {
        return jackpotRepository.findById(jackpotId).orElseThrow().getCurrentPoolAmount();
    }
}

package com.sporty.jackpot.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.sporty.jackpot.domain.ContributionType;
import com.sporty.jackpot.domain.RewardType;

/**
 * Covers {@link DataSeeder#validateSeed}: one accepted jackpot per type combination plus every
 * rejection case. Each rejection asserts the message names the offending jackpot and field, since
 * that naming is the whole point of validating at seed time rather than at bet-processing time.
 */
class DataSeederValidationTest {

    /** A fully-populated VARIABLE/VARIABLE seed — the strictest case; each test invalidates one field. */
    private static Builder valid() {
        return new Builder();
    }

    /**
     * Test-local builder over the {@link JackpotSeedProperties} record. The record itself stays a
     * plain 9-component record in production code; a builder there would exist only for tests.
     */
    private static final class Builder {
        private String id = "test-jackpot";
        private BigDecimal initialPoolAmount = new BigDecimal("1000.00");
        private ContributionType contributionType = ContributionType.VARIABLE;
        private BigDecimal contributionRate = new BigDecimal("0.10");
        private BigDecimal minContributionRate = new BigDecimal("0.02");
        private BigDecimal contributionDecayFactor = new BigDecimal("0.05");
        private RewardType rewardType = RewardType.VARIABLE;
        private BigDecimal rewardChance = new BigDecimal("0.01");
        private BigDecimal rewardPoolLimit = new BigDecimal("5000.00");

        Builder initialPoolAmount(BigDecimal v) {
            this.initialPoolAmount = v;
            return this;
        }

        Builder contributionType(ContributionType v) {
            this.contributionType = v;
            return this;
        }

        Builder contributionRate(BigDecimal v) {
            this.contributionRate = v;
            return this;
        }

        Builder minContributionRate(BigDecimal v) {
            this.minContributionRate = v;
            return this;
        }

        Builder contributionDecayFactor(BigDecimal v) {
            this.contributionDecayFactor = v;
            return this;
        }

        Builder rewardType(RewardType v) {
            this.rewardType = v;
            return this;
        }

        Builder rewardChance(BigDecimal v) {
            this.rewardChance = v;
            return this;
        }

        Builder rewardPoolLimit(BigDecimal v) {
            this.rewardPoolLimit = v;
            return this;
        }

        JackpotSeedProperties build() {
            return new JackpotSeedProperties(id, initialPoolAmount, contributionType,
                    contributionRate, minContributionRate, contributionDecayFactor,
                    rewardType, rewardChance, rewardPoolLimit);
        }
    }

    private void assertRejects(JackpotSeedProperties seed, String expectedField) {
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> DataSeeder.validateSeed(seed));
        assertTrue(ex.getMessage().contains(seed.id()),
                "message should name the jackpot: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(expectedField),
                "message should name the field '" + expectedField + "': " + ex.getMessage());
    }

    // --- accepted ---

    @Test
    void acceptsFullyPopulatedVariableJackpot() {
        assertDoesNotThrow(() -> DataSeeder.validateSeed(valid().build()));
    }

    @Test
    void acceptsFixedJackpotWithVariableOnlyFieldsNull() {
        JackpotSeedProperties seed = valid()
                .contributionType(ContributionType.FIXED)
                .minContributionRate(null)
                .contributionDecayFactor(null)
                .rewardType(RewardType.FIXED)
                .rewardPoolLimit(null)
                .build();

        assertDoesNotThrow(() -> DataSeeder.validateSeed(seed));
    }

    // --- always-required fields ---

    @Test
    void rejectsNullContributionType() {
        assertRejects(valid().contributionType(null).build(), "contributionType");
    }

    @Test
    void rejectsNullRewardType() {
        assertRejects(valid().rewardType(null).build(), "rewardType");
    }

    @Test
    void rejectsNullInitialPoolAmount() {
        assertRejects(valid().initialPoolAmount(null).build(), "initialPoolAmount");
    }

    @Test
    void rejectsZeroInitialPoolAmount() {
        // zero would be a division by zero in VariableContributionStrategy's pool growth ratio
        assertRejects(valid().initialPoolAmount(BigDecimal.ZERO).build(), "initialPoolAmount");
    }

    @Test
    void rejectsNegativeInitialPoolAmount() {
        assertRejects(valid().initialPoolAmount(new BigDecimal("-1.00")).build(), "initialPoolAmount");
    }

    @Test
    void rejectsNullContributionRate() {
        assertRejects(valid().contributionRate(null).build(), "contributionRate");
    }

    @Test
    void rejectsNullRewardChance() {
        assertRejects(valid().rewardChance(null).build(), "rewardChance");
    }

    // --- range checks ---

    @Test
    void rejectsNegativeRewardChance() {
        assertRejects(valid().rewardChance(new BigDecimal("-0.01")).build(), "rewardChance");
    }

    @Test
    void rejectsRewardChanceAboveOne() {
        assertRejects(valid().rewardChance(new BigDecimal("1.01")).build(), "rewardChance");
    }

    @Test
    void rejectsNegativeContributionRate() {
        assertRejects(valid().contributionRate(new BigDecimal("-0.01")).build(), "contributionRate");
    }

    @Test
    void rejectsContributionRateAboveOne() {
        assertRejects(valid().contributionRate(new BigDecimal("1.01")).build(), "contributionRate");
    }

    @Test
    void rejectsNegativeMinContributionRate() {
        assertRejects(valid().minContributionRate(new BigDecimal("-0.01")).build(), "minContributionRate");
    }

    @Test
    void rejectsMinContributionRateAboveOne() {
        // also exceeds contributionRate; the range check runs first
        assertRejects(valid().minContributionRate(new BigDecimal("1.01")).build(), "minContributionRate");
    }

    @Test
    void rejectsMinContributionRateAboveContributionRate() {
        JackpotSeedProperties seed = valid()
                .contributionRate(new BigDecimal("0.05"))
                .minContributionRate(new BigDecimal("0.10"))
                .build();

        assertRejects(seed, "minContributionRate");
    }

    @Test
    void acceptsMinContributionRateEqualToContributionRate() {
        JackpotSeedProperties seed = valid()
                .contributionRate(new BigDecimal("0.05"))
                .minContributionRate(new BigDecimal("0.05"))
                .build();

        assertDoesNotThrow(() -> DataSeeder.validateSeed(seed));
    }

    // --- VARIABLE contribution requirements ---

    @Test
    void rejectsVariableContributionWithNullMinContributionRate() {
        assertRejects(valid().minContributionRate(null).build(), "minContributionRate");
    }

    @Test
    void rejectsVariableContributionWithNullDecayFactor() {
        assertRejects(valid().contributionDecayFactor(null).build(), "contributionDecayFactor");
    }

    // --- VARIABLE reward requirements ---

    @Test
    void rejectsVariableRewardWithNullPoolLimit() {
        assertRejects(valid().rewardPoolLimit(null).build(), "rewardPoolLimit");
    }

    @Test
    void rejectsRewardPoolLimitEqualToInitialPoolAmount() {
        // equal would be a division by zero in VariableRewardStrategy's progress ratio
        JackpotSeedProperties seed = valid()
                .initialPoolAmount(new BigDecimal("1000.00"))
                .rewardPoolLimit(new BigDecimal("1000.00"))
                .build();

        assertRejects(seed, "rewardPoolLimit");
    }

    @Test
    void rejectsRewardPoolLimitBelowInitialPoolAmount() {
        JackpotSeedProperties seed = valid()
                .initialPoolAmount(new BigDecimal("1000.00"))
                .rewardPoolLimit(new BigDecimal("500.00"))
                .build();

        assertRejects(seed, "rewardPoolLimit");
    }
}

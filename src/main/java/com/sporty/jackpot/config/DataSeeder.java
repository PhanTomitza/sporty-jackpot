package com.sporty.jackpot.config;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.sporty.jackpot.domain.ContributionType;
import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.JackpotRepository;
import com.sporty.jackpot.domain.RewardType;

/**
 * Seeds jackpots from {@code jackpot.seed} configuration at startup. Every seeded jackpot
 * starts with {@code currentPoolAmount == initialPoolAmount} and is logged at INFO so a
 * reviewer sees the full configuration in the console.
 *
 * <p>Each seed is validated before it is persisted (see {@link #validateSeed}), so a jackpot
 * missing a field its types require fails startup rather than a later bet.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final JackpotRepository jackpotRepository;
    private final JackpotProperties jackpotProperties;

    public DataSeeder(JackpotRepository jackpotRepository, JackpotProperties jackpotProperties) {
        this.jackpotRepository = jackpotRepository;
        this.jackpotProperties = jackpotProperties;
    }

    @Override
    public void run(String... args) {
        List<JackpotSeedProperties> seeds = jackpotProperties.seed();
        if (seeds == null || seeds.isEmpty()) {
            log.warn("No jackpots configured under 'jackpot.seed' — nothing to seed");
            return;
        }
        for (JackpotSeedProperties seed : seeds) {
            validateSeed(seed);
            Jackpot jackpot = jackpotRepository.save(Jackpot.builder()
                    .id(seed.id())
                    .currentPoolAmount(seed.initialPoolAmount())
                    .initialPoolAmount(seed.initialPoolAmount())
                    .contributionType(seed.contributionType())
                    .contributionRate(seed.contributionRate())
                    .minContributionRate(seed.minContributionRate())
                    .contributionDecayFactor(seed.contributionDecayFactor())
                    .rewardType(seed.rewardType())
                    .rewardChance(seed.rewardChance())
                    .rewardPoolLimit(seed.rewardPoolLimit())
                    .build());
            log.info("Seeded jackpot '{}': initialPool={}, {}, {}",
                    jackpot.getId(),
                    jackpot.getInitialPoolAmount(),
                    describeContribution(seed),
                    describeReward(seed));
        }
    }

    /**
     * Rejects a seed jackpot that does not carry the fields its types require, or whose values are
     * out of range. Validating once at startup keeps the strategies free of defensive null checks —
     * which would otherwise have to be repeated in every future strategy — and surfaces a bad
     * configuration immediately, naming the jackpot and field, instead of as an NPE or an
     * ArithmeticException deep inside a strategy at bet-processing time.
     *
     * <p>Package-private and static so it can be unit-tested without a repository.
     *
     * @throws IllegalStateException if the seed is invalid
     */
    static void validateSeed(JackpotSeedProperties seed) {
        require(seed, seed.contributionType() != null, "contributionType", "must not be null");
        require(seed, seed.rewardType() != null, "rewardType", "must not be null");

        require(seed, seed.initialPoolAmount() != null, "initialPoolAmount", "must not be null");
        // > 0, not >= 0: VariableContributionStrategy divides pool growth by initialPoolAmount
        require(seed, seed.initialPoolAmount().compareTo(BigDecimal.ZERO) > 0,
                "initialPoolAmount", "must be greater than zero");

        require(seed, seed.contributionRate() != null, "contributionRate", "must not be null");
        requireInUnitRange(seed, seed.contributionRate(), "contributionRate");

        require(seed, seed.rewardChance() != null, "rewardChance", "must not be null");
        requireInUnitRange(seed, seed.rewardChance(), "rewardChance");

        if (seed.contributionType() == ContributionType.VARIABLE) {
            require(seed, seed.minContributionRate() != null,
                    "minContributionRate", "must not be null for a VARIABLE contribution jackpot");
            require(seed, seed.contributionDecayFactor() != null,
                    "contributionDecayFactor", "must not be null for a VARIABLE contribution jackpot");
            requireInUnitRange(seed, seed.minContributionRate(), "minContributionRate");
            // a floor above the starting rate would make the "decaying" rate rise on the first bet
            require(seed, seed.minContributionRate().compareTo(seed.contributionRate()) <= 0,
                    "minContributionRate", "must not exceed contributionRate");
        }

        if (seed.rewardType() == RewardType.VARIABLE) {
            require(seed, seed.rewardPoolLimit() != null,
                    "rewardPoolLimit", "must not be null for a VARIABLE reward jackpot");
            // strictly above: VariableRewardStrategy divides by (rewardPoolLimit - initialPoolAmount),
            // and a limit at or below the starting pool means the jackpot begins already won
            require(seed, seed.rewardPoolLimit().compareTo(seed.initialPoolAmount()) > 0,
                    "rewardPoolLimit", "must be greater than initialPoolAmount");
        }
    }

    private static void requireInUnitRange(JackpotSeedProperties seed, BigDecimal value, String field) {
        require(seed, value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(BigDecimal.ONE) <= 0,
                field, "must be between 0 and 1 inclusive, but was " + value);
    }

    private static void require(JackpotSeedProperties seed, boolean condition, String field, String problem) {
        if (!condition) {
            throw new IllegalStateException(
                    "Invalid jackpot seed '" + seed.id() + "': " + field + " " + problem);
        }
    }

    private String describeContribution(JackpotSeedProperties seed) {
        if (seed.contributionType() == ContributionType.VARIABLE) {
            return String.format("contribution=VARIABLE(start=%s, min=%s, decay=%s)",
                    seed.contributionRate(), seed.minContributionRate(), seed.contributionDecayFactor());
        }
        return String.format("contribution=FIXED(rate=%s)", seed.contributionRate());
    }

    private String describeReward(JackpotSeedProperties seed) {
        if (seed.rewardType() == RewardType.VARIABLE) {
            return String.format("reward=VARIABLE(baseChance=%s, poolLimit=%s)",
                    seed.rewardChance(), seed.rewardPoolLimit());
        }
        return String.format("reward=FIXED(chance=%s)", seed.rewardChance());
    }
}

package com.sporty.jackpot.config;

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

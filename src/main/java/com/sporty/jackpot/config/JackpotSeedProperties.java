package com.sporty.jackpot.config;

import java.math.BigDecimal;

import com.sporty.jackpot.domain.ContributionType;
import com.sporty.jackpot.domain.RewardType;

/**
 * One seed jackpot definition, bound from {@code jackpot.seed[*]} in application.yml.
 *
 * <p>Optional (VARIABLE-only) fields may be null: {@code minContributionRate} and
 * {@code contributionDecayFactor} only for VARIABLE contribution, {@code rewardPoolLimit}
 * only for VARIABLE reward.
 */
public record JackpotSeedProperties(
        String id,
        BigDecimal initialPoolAmount,
        ContributionType contributionType,
        BigDecimal contributionRate,
        BigDecimal minContributionRate,
        BigDecimal contributionDecayFactor,
        RewardType rewardType,
        BigDecimal rewardChance,
        BigDecimal rewardPoolLimit) {
}

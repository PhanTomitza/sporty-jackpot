package com.sporty.jackpot.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.sporty.jackpot.domain.ContributionType;
import com.sporty.jackpot.domain.Jackpot;

/**
 * Fixed contribution: a flat percentage of the bet amount, independent of the pool.
 *
 * <p>{@code contribution = betAmount * contributionRate}, rounded to scale 2 (HALF_UP).
 */
@Component
public class FixedContributionStrategy implements ContributionStrategy {

    @Override
    public ContributionType supports() {
        return ContributionType.FIXED;
    }

    @Override
    public BigDecimal calculateContribution(BigDecimal betAmount, Jackpot jackpot) {
        return betAmount.multiply(jackpot.getContributionRate())
                .setScale(2, RoundingMode.HALF_UP);
    }
}

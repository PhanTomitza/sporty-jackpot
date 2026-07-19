package com.sporty.jackpot.strategy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.sporty.jackpot.domain.ContributionType;
import com.sporty.jackpot.domain.Jackpot;

/**
 * Variable contribution: the rate starts at {@code contributionRate} and decreases linearly as
 * the pool grows above its initial value, floored at {@code minContributionRate}.
 *
 * <pre>
 *   poolGrowthRatio = max(0, (currentPoolAmount - initialPoolAmount) / initialPoolAmount)
 *   effectiveRate   = max(minContributionRate, contributionRate - contributionDecayFactor * poolGrowthRatio)
 *   contribution    = betAmount * effectiveRate
 * </pre>
 *
 * <p>The spec does not mandate a floor. It exists because the linear decay model chosen here is
 * unbounded: without a floor the effective rate would eventually go negative, and a negative rate
 * would drain the pool rather than contribute to it — no longer a contribution at all.
 */
@Component
public class VariableContributionStrategy implements ContributionStrategy {

    /** Rounding for intermediate ratios; avoids ArithmeticException on non-terminating divisions. */
    private static final MathContext MC = MathContext.DECIMAL64;

    @Override
    public ContributionType supports() {
        return ContributionType.VARIABLE;
    }

    @Override
    public BigDecimal calculateContribution(BigDecimal betAmount, Jackpot jackpot) {
        BigDecimal initial = jackpot.getInitialPoolAmount();
        BigDecimal poolGrowthRatio = jackpot.getCurrentPoolAmount()
                .subtract(initial)
                .divide(initial, MC)
                .max(BigDecimal.ZERO);

        BigDecimal decayedRate = jackpot.getContributionRate()
                .subtract(jackpot.getContributionDecayFactor().multiply(poolGrowthRatio, MC));

        BigDecimal effectiveRate = decayedRate.max(jackpot.getMinContributionRate());

        return betAmount.multiply(effectiveRate).setScale(2, RoundingMode.HALF_UP);
    }
}

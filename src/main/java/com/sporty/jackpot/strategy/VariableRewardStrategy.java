package com.sporty.jackpot.strategy;

import java.math.BigDecimal;
import java.math.MathContext;

import org.springframework.stereotype.Component;

import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.RewardType;

/**
 * Variable reward: the win chance equals the base {@code rewardChance} when the pool sits at its
 * initial value and grows linearly to 1.0 as the pool approaches {@code rewardPoolLimit}. At or
 * above the limit the chance is exactly 1.0.
 *
 * <pre>
 *   if currentPoolAmount >= rewardPoolLimit -> 1.0
 *   progress  = clamp01((currentPoolAmount - initialPoolAmount) / (rewardPoolLimit - initialPoolAmount))
 *   winChance = rewardChance + (1 - rewardChance) * progress
 * </pre>
 *
 * <p>Progress is measured from {@code initialPoolAmount}, not from zero: a freshly reset jackpot
 * (pool == initial) must start at the base chance, per the spec's "in the beginning the chance
 * for reward is smaller". This mirrors {@link VariableContributionStrategy}, which also measures
 * pool growth relative to the initial value.
 */
@Component
public class VariableRewardStrategy implements RewardStrategy {

    /** Rounding for intermediate ratios; avoids ArithmeticException on non-terminating divisions. */
    private static final MathContext MC = MathContext.DECIMAL64;

    @Override
    public RewardType supports() {
        return RewardType.VARIABLE;
    }

    @Override
    public BigDecimal winChance(Jackpot jackpot) {
        BigDecimal current = jackpot.getCurrentPoolAmount();
        BigDecimal initial = jackpot.getInitialPoolAmount();
        BigDecimal limit = jackpot.getRewardPoolLimit();

        if (current.compareTo(limit) >= 0) {
            return BigDecimal.ONE;
        }

        // The clamp is currently unreachable: the early return above covers current >= limit, and a
        // pool never falls below its initial value, so progress is already within [0, 1]. Retained
        // to enforce the interface's [0, 1] contract for future callers.
        BigDecimal progress = current.subtract(initial)
                .divide(limit.subtract(initial), MC)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.ONE);

        BigDecimal base = jackpot.getRewardChance();
        return base.add(BigDecimal.ONE.subtract(base).multiply(progress, MC));
    }
}

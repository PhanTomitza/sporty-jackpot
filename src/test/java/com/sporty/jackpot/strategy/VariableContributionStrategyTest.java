package com.sporty.jackpot.strategy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.sporty.jackpot.domain.ContributionType;
import com.sporty.jackpot.domain.Jackpot;

class VariableContributionStrategyTest {

    private final VariableContributionStrategy strategy = new VariableContributionStrategy();

    private Jackpot jackpot(String current, String initial, String rate, String min, String decay) {
        return Jackpot.builder()
                .currentPoolAmount(new BigDecimal(current))
                .initialPoolAmount(new BigDecimal(initial))
                .contributionRate(new BigDecimal(rate))
                .minContributionRate(new BigDecimal(min))
                .contributionDecayFactor(new BigDecimal(decay))
                .build();
    }

    @Test
    void supportsVariable() {
        assertEquals(ContributionType.VARIABLE, strategy.supports());
    }

    @Test
    void atPoolEqualToInitialRateEqualsContributionRate() {
        // pool == initial -> growth 0 -> effective rate == contributionRate (0.10)
        Jackpot jackpot = jackpot("1000", "1000", "0.10", "0.02", "0.05");

        BigDecimal contribution = strategy.calculateContribution(new BigDecimal("100"), jackpot);

        // 100 * 0.10 = 10.00 exactly; any decay would lower this
        assertEquals(0, contribution.compareTo(new BigDecimal("10.00")));
    }

    @Test
    void rateStrictlyDecreasesAsPoolGrows() {
        BigDecimal bet = new BigDecimal("100");
        BigDecimal atInitial = strategy.calculateContribution(bet, jackpot("1000", "1000", "0.10", "0.02", "0.05"));
        BigDecimal atMid = strategy.calculateContribution(bet, jackpot("1500", "1000", "0.10", "0.02", "0.05"));
        BigDecimal atHigh = strategy.calculateContribution(bet, jackpot("2000", "1000", "0.10", "0.02", "0.05"));

        // rates 0.10 > 0.075 > 0.05 -> contributions 10.00 > 7.50 > 5.00
        assertTrue(atInitial.compareTo(atMid) > 0);
        assertTrue(atMid.compareTo(atHigh) > 0);
    }

    @Test
    void rateNeverFallsBelowMinimumEvenAtExtremePool() {
        // 100x initial -> raw rate 0.10 - 0.05*99 = -4.85 -> floored to min 0.02
        Jackpot jackpot = jackpot("100000", "1000", "0.10", "0.02", "0.05");

        BigDecimal contribution = strategy.calculateContribution(new BigDecimal("100"), jackpot);

        // 100 * 0.02 = 2.00
        assertEquals(0, contribution.compareTo(new BigDecimal("2.00")));
        assertTrue(contribution.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void noArithmeticExceptionOnNonTerminatingGrowthRatio() {
        // (5000 - 3000) / 3000 = 2/3, a non-terminating decimal
        Jackpot jackpot = jackpot("5000", "3000", "0.10", "0.02", "0.05");

        assertDoesNotThrow(() -> strategy.calculateContribution(new BigDecimal("100"), jackpot));
    }
}

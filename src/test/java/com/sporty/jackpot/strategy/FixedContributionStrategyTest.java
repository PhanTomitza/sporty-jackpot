package com.sporty.jackpot.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.sporty.jackpot.domain.ContributionType;
import com.sporty.jackpot.domain.Jackpot;

class FixedContributionStrategyTest {

    private final FixedContributionStrategy strategy = new FixedContributionStrategy();

    @Test
    void supportsFixed() {
        assertEquals(ContributionType.FIXED, strategy.supports());
    }

    @Test
    void calculatesFlatPercentageOfBet() {
        Jackpot jackpot = Jackpot.builder()
                .contributionRate(new BigDecimal("0.05"))
                .build();

        BigDecimal contribution = strategy.calculateContribution(new BigDecimal("100.00"), jackpot);

        assertEquals(0, contribution.compareTo(new BigDecimal("5.00")));
    }

    @Test
    void roundsFinalContributionToScaleTwo() {
        Jackpot jackpot = Jackpot.builder()
                .contributionRate(new BigDecimal("0.05"))
                .build();

        // 10.01 * 0.05 = 0.5005 -> HALF_UP scale 2 -> 0.50
        BigDecimal contribution = strategy.calculateContribution(new BigDecimal("10.01"), jackpot);

        assertEquals(2, contribution.scale());
        assertEquals(0, contribution.compareTo(new BigDecimal("0.50")));
    }
}

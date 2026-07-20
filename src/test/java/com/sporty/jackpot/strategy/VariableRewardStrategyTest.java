package com.sporty.jackpot.strategy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.RewardType;

class VariableRewardStrategyTest {

    private final VariableRewardStrategy strategy = new VariableRewardStrategy();

    private Jackpot jackpot(String current, String initial, String chance, String limit) {
        return Jackpot.builder()
                .currentPoolAmount(new BigDecimal(current))
                .initialPoolAmount(new BigDecimal(initial))
                .rewardChance(new BigDecimal(chance))
                .rewardPoolLimit(new BigDecimal(limit))
                .build();
    }

    @Test
    void supportsVariable() {
        assertEquals(RewardType.VARIABLE, strategy.supports());
    }

    @Test
    void atPoolEqualToInitialChanceEqualsBaseChance() {
        // progress 0 -> chance == base rewardChance (0.01)
        Jackpot jackpot = jackpot("1000", "1000", "0.01", "5000");

        assertEquals(0, strategy.winChance(jackpot).compareTo(new BigDecimal("0.01")));
    }

    @Test
    void chanceIsStrictlyGreaterAtIntermediatePool() {
        BigDecimal base = new BigDecimal("0.01");
        BigDecimal atInitial = strategy.winChance(jackpot("1000", "1000", "0.01", "5000"));
        BigDecimal atMid = strategy.winChance(jackpot("3000", "1000", "0.01", "5000"));

        assertTrue(atMid.compareTo(atInitial) > 0);
        assertTrue(atMid.compareTo(base) > 0);
        assertTrue(atMid.compareTo(BigDecimal.ONE) < 0);
    }

    @Test
    void chanceIsExactlyOneAtLimitAndAbove() {
        assertEquals(0, strategy.winChance(jackpot("5000", "1000", "0.01", "5000")).compareTo(BigDecimal.ONE));
        assertEquals(0, strategy.winChance(jackpot("6000", "1000", "0.01", "5000")).compareTo(BigDecimal.ONE));
    }

    @Test
    void chanceNeverExceedsOneJustBelowLimit() {
        // pool just below the limit -> progress ~0.99975 -> chance < 1
        BigDecimal chance = strategy.winChance(jackpot("4999", "1000", "0.01", "5000"));

        assertTrue(chance.compareTo(BigDecimal.ONE) <= 0);
        assertTrue(chance.compareTo(new BigDecimal("0.01")) > 0);
    }

    @Test
    void noArithmeticExceptionOnNonTerminatingProgressRatio() {
        // (2000 - 1000) / (4000 - 1000) = 1/3, a non-terminating decimal
        Jackpot jackpot = jackpot("2000", "1000", "0.01", "4000");

        assertDoesNotThrow(() -> strategy.winChance(jackpot));
    }
}

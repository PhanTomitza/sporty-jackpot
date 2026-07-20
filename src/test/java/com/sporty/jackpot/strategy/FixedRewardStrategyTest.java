package com.sporty.jackpot.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.RewardType;

class FixedRewardStrategyTest {

    private final FixedRewardStrategy strategy = new FixedRewardStrategy();

    @Test
    void supportsFixed() {
        assertEquals(RewardType.FIXED, strategy.supports());
    }

    @Test
    void winChanceEqualsConfiguredRewardChance() {
        Jackpot jackpot = Jackpot.builder()
                .rewardChance(new BigDecimal("0.10"))
                .build();

        assertEquals(0, strategy.winChance(jackpot).compareTo(new BigDecimal("0.10")));
    }
}

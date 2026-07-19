package com.sporty.jackpot.strategy;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.RewardType;

/**
 * Fixed reward: a flat win chance, independent of the pool. {@code winChance = rewardChance}.
 */
@Component
public class FixedRewardStrategy implements RewardStrategy {

    @Override
    public RewardType supports() {
        return RewardType.FIXED;
    }

    @Override
    public BigDecimal winChance(Jackpot jackpot) {
        return jackpot.getRewardChance();
    }
}

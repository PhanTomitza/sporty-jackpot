package com.sporty.jackpot.strategy;

import java.math.BigDecimal;

import com.sporty.jackpot.domain.Jackpot;
import com.sporty.jackpot.domain.RewardType;

/**
 * Computes the probability that a bet wins the jackpot. One implementation per
 * {@link RewardType}; new types are added by adding a new implementation, never by editing
 * existing ones (open/closed).
 */
public interface RewardStrategy {

    /** The reward type this strategy handles. */
    RewardType supports();

    /** The win probability in [0, 1] for the jackpot's current state. Not money — not rounded. */
    BigDecimal winChance(Jackpot jackpot);
}

package com.sporty.jackpot.service;

import java.math.BigDecimal;

/**
 * The outcome of evaluating one bet against its jackpot.
 *
 * <p>{@code rewardAmount} is null exactly when {@code won} is false. Null rather than
 * {@code ZERO}: a losing bet has no reward, which is not the same statement as a reward of zero,
 * and a pool can never legitimately pay out 0.00 anyway.
 */
public record RewardResult(String betId, boolean won, BigDecimal rewardAmount) {

    public static RewardResult won(String betId, BigDecimal rewardAmount) {
        return new RewardResult(betId, true, rewardAmount);
    }

    public static RewardResult lost(String betId) {
        return new RewardResult(betId, false, null);
    }
}

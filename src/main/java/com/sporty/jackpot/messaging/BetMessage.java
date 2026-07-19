package com.sporty.jackpot.messaging;

import java.math.BigDecimal;

/**
 * The payload published to the {@code jackpot-bets} topic, serialized as JSON.
 *
 * <p>Deliberately separate from the {@code Bet} entity: this is a wire contract shared with any
 * other service producing bets, and it must not drift just because the persistence model changes.
 * It carries no {@code createdAt} — the consumer stamps that when it persists the bet, so the
 * timestamp reflects processing time rather than a producer's clock.
 */
public record BetMessage(
        String betId,
        String userId,
        String jackpotId,
        BigDecimal betAmount) {
}

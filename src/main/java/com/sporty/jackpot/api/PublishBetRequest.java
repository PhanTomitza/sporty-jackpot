package com.sporty.jackpot.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code POST /api/bets}.
 *
 * <p>{@code betAmount} is {@code @Positive} rather than {@code @PositiveOrZero}: a zero-amount bet
 * would contribute nothing to a pool while still occupying a bet id, so it is rejected at the edge
 * instead of becoming a no-op record.
 */
public record PublishBetRequest(

        @NotBlank(message = "must not be blank")
        String betId,

        @NotBlank(message = "must not be blank")
        String userId,

        @NotBlank(message = "must not be blank")
        String jackpotId,

        @NotNull(message = "must not be null")
        @Positive(message = "must be greater than zero")
        BigDecimal betAmount) {
}

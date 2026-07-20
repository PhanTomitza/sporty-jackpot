package com.sporty.jackpot.messaging;

/**
 * Publishes a bet for asynchronous processing. Two implementations, selected by profile:
 * {@link KafkaBetPublisher} ({@code kafka}) and {@link LoggingBetPublisher} ({@code mock}).
 *
 * <p>This is the one interface in the codebase with more than one implementation that is not a
 * strategy — it exists so the controller is identical in both profiles.
 */
public interface BetPublisher {

    void publish(BetMessage bet);
}

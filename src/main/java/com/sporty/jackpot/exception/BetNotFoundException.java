package com.sporty.jackpot.exception;

/**
 * Thrown when an evaluation is requested for a {@code betId} that was never processed.
 *
 * <p>A bet row exists only once the bet has been consumed and its contribution applied, so an
 * unknown {@code betId} does not mean "a bet that lost" — it means there is no such bet to
 * evaluate. That is a client error (404), not an empty result.
 */
public class BetNotFoundException extends RuntimeException {

    public BetNotFoundException(String betId) {
        super("No processed bet found with id '" + betId + "'");
    }
}

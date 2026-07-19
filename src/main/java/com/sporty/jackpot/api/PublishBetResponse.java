package com.sporty.jackpot.api;

/**
 * Response body for an accepted bet. {@code status} is always {@code ACCEPTED} — it reports that
 * the bet was taken for asynchronous processing, not that a contribution was applied.
 */
public record PublishBetResponse(String betId, String status) {

    private static final String ACCEPTED = "ACCEPTED";

    public static PublishBetResponse accepted(String betId) {
        return new PublishBetResponse(betId, ACCEPTED);
    }
}

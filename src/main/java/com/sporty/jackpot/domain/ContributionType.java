package com.sporty.jackpot.domain;

/**
 * How a jackpot's contribution rate is determined.
 *
 * <ul>
 *   <li>{@code FIXED} — a flat rate applied to every bet.</li>
 *   <li>{@code VARIABLE} — a rate that decays as the pool grows, floored at a minimum.</li>
 * </ul>
 */
public enum ContributionType {
    FIXED,
    VARIABLE
}

package com.sporty.jackpot.domain;

/**
 * How a jackpot's win chance is determined.
 *
 * <ul>
 *   <li>{@code FIXED} — a flat win chance for every evaluated bet.</li>
 *   <li>{@code VARIABLE} — a chance that grows with the pool, reaching 100% at a pool limit.</li>
 * </ul>
 */
public enum RewardType {
    FIXED,
    VARIABLE
}

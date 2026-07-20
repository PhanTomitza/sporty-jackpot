package com.sporty.jackpot.config;

/**
 * Supplies the random draw used to decide whether a bet wins.
 *
 * <p>This exists purely so randomness is injectable. A direct {@code Math.random()} call inside
 * {@code RewardEvaluationService} would leave the single most important branch in the service —
 * win versus lose — impossible to test deterministically: the win path could only be reached by
 * looping until chance produced it, which is a flaky test, not a test. Behind this interface a
 * stub returning a fixed value makes both branches reachable on demand.
 *
 * <p>This is the one place the "no interfaces for single-implementation services" rule is
 * deliberately broken, because the second implementation is the test stub — the interface has two
 * implementations by design, not one.
 */
public interface RandomProvider {

    /** A uniformly distributed value in {@code [0, 1)}. */
    double nextDouble();
}

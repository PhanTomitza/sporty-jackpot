package com.sporty.jackpot.config;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

/**
 * The production {@link RandomProvider}, delegating to {@link ThreadLocalRandom}.
 *
 * <p>{@code ThreadLocalRandom} rather than a shared {@link java.util.Random}: evaluation happens on
 * HTTP request threads, and a shared {@code Random} makes every concurrent request contend on one
 * atomic seed. A per-thread generator has no contention and needs no synchronisation of its own.
 */
@Component
public class ThreadLocalRandomProvider implements RandomProvider {

    @Override
    public double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }
}

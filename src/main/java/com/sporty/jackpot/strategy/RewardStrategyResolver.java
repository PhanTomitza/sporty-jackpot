package com.sporty.jackpot.strategy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sporty.jackpot.domain.RewardType;

/**
 * Resolves the {@link RewardStrategy} for a {@link RewardType}. Spring injects every
 * implementation via collection injection, so adding a new strategy requires zero edits here.
 * Resolution is a pure map lookup — no switch/if-chain. Fails fast at construction if two
 * strategies declare the same {@link RewardStrategy#supports()}.
 */
@Component
public class RewardStrategyResolver {

    private final Map<RewardType, RewardStrategy> strategiesByType;

    public RewardStrategyResolver(List<RewardStrategy> strategies) {
        this.strategiesByType = Collections.unmodifiableMap(strategies.stream()
                .collect(Collectors.toMap(
                        RewardStrategy::supports,
                        Function.identity(),
                        (existing, duplicate) -> {
                            throw new IllegalStateException(
                                    "Multiple RewardStrategy beans declare supports()="
                                            + existing.supports() + ": "
                                            + existing.getClass().getSimpleName() + " and "
                                            + duplicate.getClass().getSimpleName());
                        },
                        () -> new EnumMap<>(RewardType.class))));
    }

    /**
     * @throws IllegalArgumentException if {@code type} is null — a caller error
     * @throws IllegalStateException if no strategy is registered for {@code type} — a wiring problem
     */
    public RewardStrategy resolve(RewardType type) {
        if (type == null) {
            throw new IllegalArgumentException("RewardType must not be null");
        }
        RewardStrategy strategy = strategiesByType.get(type);
        if (strategy == null) {
            throw new IllegalStateException("No RewardStrategy registered for type " + type);
        }
        return strategy;
    }
}

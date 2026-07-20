package com.sporty.jackpot.strategy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sporty.jackpot.domain.ContributionType;

/**
 * Resolves the {@link ContributionStrategy} for a {@link ContributionType}. Spring injects every
 * implementation via collection injection, so adding a new strategy requires zero edits here.
 * Resolution is a pure map lookup — no switch/if-chain. Fails fast at construction if two
 * strategies declare the same {@link ContributionStrategy#supports()}.
 */
@Component
public class ContributionStrategyResolver {

    private final Map<ContributionType, ContributionStrategy> strategiesByType;

    public ContributionStrategyResolver(List<ContributionStrategy> strategies) {
        this.strategiesByType = Collections.unmodifiableMap(strategies.stream()
                .collect(Collectors.toMap(
                        ContributionStrategy::supports,
                        Function.identity(),
                        (existing, duplicate) -> {
                            throw new IllegalStateException(
                                    "Multiple ContributionStrategy beans declare supports()="
                                            + existing.supports() + ": "
                                            + existing.getClass().getSimpleName() + " and "
                                            + duplicate.getClass().getSimpleName());
                        },
                        () -> new EnumMap<>(ContributionType.class))));
    }

    /**
     * @throws IllegalArgumentException if {@code type} is null — a caller error
     * @throws IllegalStateException if no strategy is registered for {@code type} — a wiring problem
     */
    public ContributionStrategy resolve(ContributionType type) {
        if (type == null) {
            throw new IllegalArgumentException("ContributionType must not be null");
        }
        ContributionStrategy strategy = strategiesByType.get(type);
        if (strategy == null) {
            throw new IllegalStateException("No ContributionStrategy registered for type " + type);
        }
        return strategy;
    }
}

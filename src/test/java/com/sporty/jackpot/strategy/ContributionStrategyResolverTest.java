package com.sporty.jackpot.strategy;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sporty.jackpot.domain.ContributionType;

class ContributionStrategyResolverTest {

    private final FixedContributionStrategy fixed = new FixedContributionStrategy();
    private final VariableContributionStrategy variable = new VariableContributionStrategy();

    @Test
    void resolvesCorrectStrategyPerType() {
        ContributionStrategyResolver resolver = new ContributionStrategyResolver(List.of(fixed, variable));

        assertSame(fixed, resolver.resolve(ContributionType.FIXED));
        assertSame(variable, resolver.resolve(ContributionType.VARIABLE));
    }

    @Test
    void unknownTypeThrowsIllegalState() {
        // only FIXED registered -> VARIABLE is unknown
        ContributionStrategyResolver resolver = new ContributionStrategyResolver(List.of(fixed));

        assertThrows(IllegalStateException.class, () -> resolver.resolve(ContributionType.VARIABLE));
    }

    @Test
    void nullTypeThrowsIllegalArgument() {
        // a null argument is a caller error, distinct from an unregistered type
        ContributionStrategyResolver resolver = new ContributionStrategyResolver(List.of(fixed, variable));

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null));
    }

    @Test
    void duplicateSupportsFailsAtConstruction() {
        assertThrows(IllegalStateException.class,
                () -> new ContributionStrategyResolver(List.of(fixed, new FixedContributionStrategy())));
    }
}

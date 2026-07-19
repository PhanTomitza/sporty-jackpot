package com.sporty.jackpot.strategy;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sporty.jackpot.domain.RewardType;

class RewardStrategyResolverTest {

    private final FixedRewardStrategy fixed = new FixedRewardStrategy();
    private final VariableRewardStrategy variable = new VariableRewardStrategy();

    @Test
    void resolvesCorrectStrategyPerType() {
        RewardStrategyResolver resolver = new RewardStrategyResolver(List.of(fixed, variable));

        assertSame(fixed, resolver.resolve(RewardType.FIXED));
        assertSame(variable, resolver.resolve(RewardType.VARIABLE));
    }

    @Test
    void unknownTypeThrowsIllegalState() {
        // only FIXED registered -> VARIABLE is unknown
        RewardStrategyResolver resolver = new RewardStrategyResolver(List.of(fixed));

        assertThrows(IllegalStateException.class, () -> resolver.resolve(RewardType.VARIABLE));
    }

    @Test
    void nullTypeThrowsIllegalArgument() {
        // a null argument is a caller error, distinct from an unregistered type
        RewardStrategyResolver resolver = new RewardStrategyResolver(List.of(fixed, variable));

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null));
    }

    @Test
    void duplicateSupportsFailsAtConstruction() {
        assertThrows(IllegalStateException.class,
                () -> new RewardStrategyResolver(List.of(fixed, new FixedRewardStrategy())));
    }
}

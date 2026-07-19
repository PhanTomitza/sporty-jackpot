package com.sporty.jackpot.strategy;

import java.math.BigDecimal;

import com.sporty.jackpot.domain.ContributionType;
import com.sporty.jackpot.domain.Jackpot;

/**
 * Computes how much a bet contributes to a jackpot pool. One implementation per
 * {@link ContributionType}; new types are added by adding a new implementation, never by editing
 * existing ones (open/closed).
 */
public interface ContributionStrategy {

    /** The contribution type this strategy handles. */
    ContributionType supports();

    /** The monetary amount (scale 2) to add to the pool for the given bet. */
    BigDecimal calculateContribution(BigDecimal betAmount, Jackpot jackpot);
}

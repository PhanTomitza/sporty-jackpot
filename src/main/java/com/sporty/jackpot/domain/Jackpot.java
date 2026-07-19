package com.sporty.jackpot.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A jackpot pool. The id is a natural identifier (e.g. {@code "classic-progressive"}), never generated.
 *
 * <p>A jackpot only populates the config fields its {@link #contributionType} and
 * {@link #rewardType} require; the rest may stay null. The config fields are exactly:
 * {@code contributionRate}, {@code minContributionRate}, {@code contributionDecayFactor},
 * {@code rewardChance}, {@code rewardPoolLimit} — and no others.
 *
 * <p>Their meaning depends on the type:
 * <ul>
 *   <li>{@code contributionRate} — the flat rate when {@code contributionType} is FIXED,
 *       and the STARTING rate when VARIABLE.</li>
 *   <li>{@code minContributionRate} and {@code contributionDecayFactor} — apply only to
 *       VARIABLE contribution.</li>
 *   <li>{@code rewardChance} — the flat chance when {@code rewardType} is FIXED,
 *       and the BASE chance when VARIABLE.</li>
 *   <li>{@code rewardPoolLimit} — applies only to VARIABLE reward.</li>
 * </ul>
 */
@Entity
@Table(name = "jackpot")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Jackpot {

    @Id
    private String id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal currentPoolAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal initialPoolAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContributionType contributionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RewardType rewardType;

    // --- Contribution config (rates are scale 6: computed variable rates carry >2 decimals) ---
    /** FIXED: the flat rate. VARIABLE: the starting rate. */
    @Column(precision = 19, scale = 6)
    private BigDecimal contributionRate;

    /** VARIABLE only: the floor the decaying rate never drops below. */
    @Column(precision = 19, scale = 6)
    private BigDecimal minContributionRate;

    /** VARIABLE only: how quickly the rate decays as the pool grows. */
    @Column(precision = 19, scale = 6)
    private BigDecimal contributionDecayFactor;

    // --- Reward config ---
    /** FIXED: the flat win chance. VARIABLE: the base win chance. In [0, 1]. */
    @Column(precision = 19, scale = 6)
    private BigDecimal rewardChance;

    /** VARIABLE only: the pool value at which win chance reaches 100%. Money, so scale 2. */
    @Column(precision = 19, scale = 2)
    private BigDecimal rewardPoolLimit;

    // --- Pool mutation ---------------------------------------------------------------------
    // These two methods are the ONLY way the pool changes, and there is deliberately no
    // setCurrentPoolAmount. A setter would let any caller assign an arbitrary pool value; these
    // express the only two legal transitions the domain has, so an illegal pool state is
    // unreachable by construction rather than merely discouraged by convention. The entity owns
    // its own invariant instead of trusting every service that touches it.

    /**
     * Adds {@code amount} to the pool. Re-scaled to 2 so the pool stays a monetary value even if a
     * strategy hands back something at a wider scale.
     */
    public void contribute(BigDecimal amount) {
        this.currentPoolAmount = this.currentPoolAmount.add(amount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns the pool to its initial value. Used after a win, when the reward paid out equals the
     * whole pool and the jackpot starts accumulating again from its seed value.
     */
    public void resetPool() {
        this.currentPoolAmount = this.initialPoolAmount;
    }
}

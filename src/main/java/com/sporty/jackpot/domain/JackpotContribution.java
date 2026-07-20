package com.sporty.jackpot.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An audit record of a single bet's contribution to a jackpot pool.
 */
@Entity
@Table(name = "jackpot_contribution")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JackpotContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String betId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String jackpotId;

    /** The bet amount this contribution was taken from. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal stakeAmount;

    /** The portion of the stake added to the pool. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal contributionAmount;

    /** The pool value AFTER this contribution was applied. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal currentJackpotAmount;

    @Column(nullable = false)
    private Instant createdAt;
}

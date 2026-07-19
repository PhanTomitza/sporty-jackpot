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
 * An audit record of a jackpot win: the reward paid out for a winning bet.
 */
@Entity
@Table(name = "jackpot_reward")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JackpotReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String betId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String jackpotId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal jackpotRewardAmount;

    @Column(nullable = false)
    private Instant createdAt;
}

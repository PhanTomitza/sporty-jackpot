package com.sporty.jackpot.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An audit record of a jackpot win: the reward paid out for a winning bet.
 *
 * <p><b>{@code bet_id} is unique.</b> One bet can win at most once, and this makes that a database
 * invariant rather than a convention. It is defence in depth, not the primary mechanism: the
 * pessimistic write lock taken on the jackpot in {@code RewardEvaluationService} is what actually
 * serialises concurrent evaluations, and under that lock a second reward row is never attempted.
 * The constraint exists for the cases the lock cannot cover — a code path that forgets to take it,
 * a future caller writing rewards directly, a bulk fix applied by hand against the database. Those
 * fail loudly here instead of silently paying a jackpot out twice.
 */
@Entity
@Table(
        name = "jackpot_reward",
        uniqueConstraints = @UniqueConstraint(name = "uk_jackpot_reward_bet_id", columnNames = "bet_id"))
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

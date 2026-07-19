package com.sporty.jackpot.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A bet placed by a user against a jackpot, as received from the {@code jackpot-bets} topic.
 */
@Entity
@Table(name = "bet")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Bet {

    @Id
    private String betId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String jackpotId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal betAmount;

    @Column(nullable = false)
    private Instant createdAt;
}

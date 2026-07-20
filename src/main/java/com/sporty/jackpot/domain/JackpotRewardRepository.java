package com.sporty.jackpot.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JackpotRewardRepository extends JpaRepository<JackpotReward, Long> {

    Optional<JackpotReward> findByBetId(String betId);
}

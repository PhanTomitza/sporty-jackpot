package com.sporty.jackpot.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BetRepository extends JpaRepository<Bet, String> {
}

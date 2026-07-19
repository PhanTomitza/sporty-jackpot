package com.sporty.jackpot.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface JackpotRepository extends JpaRepository<Jackpot, String> {

    /**
     * Loads a jackpot by id under a pessimistic write lock so concurrent contributions
     * serialize on the pool row while it is being updated.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Jackpot j where j.id = :id")
    Optional<Jackpot> findWithLockById(@Param("id") String id);
}

package com.sporty.jackpot.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code jackpot.*} configuration. {@code seed} holds the jackpots to create at
 * startup, making the initial pool value (and every strategy parameter) configurable via
 * application.yml rather than hardcoded.
 */
@ConfigurationProperties(prefix = "jackpot")
public record JackpotProperties(List<JackpotSeedProperties> seed) {
}

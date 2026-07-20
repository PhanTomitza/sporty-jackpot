package com.sporty.jackpot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code jackpot.kafka.*}. The topic name lives here rather than as a constant in Java so
 * it can be pointed at a different topic per environment without a rebuild; no Java code contains
 * a literal topic string.
 *
 * <p>Bound outside any profile: the topic is the same name whether it is being published to
 * (kafka profile) or only logged (mock profile).
 */
@ConfigurationProperties(prefix = "jackpot.kafka")
public record JackpotKafkaProperties(String topic) {
}

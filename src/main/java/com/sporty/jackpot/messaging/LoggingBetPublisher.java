package com.sporty.jackpot.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.sporty.jackpot.config.JackpotKafkaProperties;

/**
 * Logs the bet payload instead of publishing it. Active under the {@code mock} profile (the
 * default), so the whole service runs with no broker.
 *
 * <p>Explicitly permitted by the assignment: "If the Kafka setup is too complex, use mocks for the
 * Kafka producer. Just log the payload."
 */
@Component
@Profile("mock")
public class LoggingBetPublisher implements BetPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingBetPublisher.class);

    private final JackpotKafkaProperties kafkaProperties;

    public LoggingBetPublisher(JackpotKafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Override
    public void publish(BetMessage bet) {
        // The key is logged even though nothing partitions here, so the mock output shows the same
        // routing decision the real publisher makes.
        log.info("[MOCK] Publishing to topic '{}' (key={}): betId={}, userId={}, jackpotId={}, betAmount={}",
                kafkaProperties.topic(),
                bet.jackpotId(),
                bet.betId(),
                bet.userId(),
                bet.jackpotId(),
                bet.betAmount());

        // TODO(Phase 4): invoke the same downstream bet-processing entry point that the real Kafka
        // consumer will call, so the mock profile exercises contribution logic end-to-end without a
        // broker. Deliberately not implemented here — Phase 3 is publishing only, and the
        // contribution logic must live in the shared processing path, never in a publisher.
    }
}

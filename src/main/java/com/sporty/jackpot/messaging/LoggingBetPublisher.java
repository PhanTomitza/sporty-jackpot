package com.sporty.jackpot.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.sporty.jackpot.config.JackpotKafkaProperties;
import com.sporty.jackpot.service.JackpotContributionService;

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
    private final JackpotContributionService contributionService;

    public LoggingBetPublisher(JackpotKafkaProperties kafkaProperties,
            JackpotContributionService contributionService) {
        this.kafkaProperties = kafkaProperties;
        this.contributionService = contributionService;
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

        // Stands in for the broker round trip: this is the same entry point KafkaBetConsumer
        // calls, so the mock profile exercises the identical contribution path with no broker.
        // Note this is a direct, synchronous call, whereas the Kafka path is asynchronous — the
        // processing is identical, the delivery is not. That difference is why the pool still
        // needs its pessimistic lock here: with no partition to serialise anything, concurrent
        // HTTP requests for one jackpot land on this method on different threads at once.
        contributionService.processBet(bet);
    }
}

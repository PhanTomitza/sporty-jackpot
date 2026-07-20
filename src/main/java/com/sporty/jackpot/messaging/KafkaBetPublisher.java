package com.sporty.jackpot.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.sporty.jackpot.config.JackpotKafkaProperties;

/**
 * Publishes bets to Kafka. Active under the {@code kafka} profile.
 *
 * <p><b>The message key is the jackpotId, not the betId.</b> Kafka partitions by key and
 * guarantees ordering only within a partition, so keying by jackpotId puts every bet for one
 * jackpot on one partition, where a single consumer processes them in order — which serialises
 * updates to that jackpot's pool. Keying by betId would spread bets for the same jackpot across
 * every partition, letting concurrent consumers apply contributions to one pool simultaneously.
 */
@Component
@Profile("kafka")
public class KafkaBetPublisher implements BetPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaBetPublisher.class);

    private final KafkaTemplate<String, BetMessage> kafkaTemplate;
    private final JackpotKafkaProperties kafkaProperties;

    public KafkaBetPublisher(KafkaTemplate<String, BetMessage> kafkaTemplate,
            JackpotKafkaProperties kafkaProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
    }

    @Override
    public void publish(BetMessage bet) {
        String topic = kafkaProperties.topic();
        // send() is async and returns a CompletableFuture; the callback only logs, because the
        // endpoint has already answered 202 and there is no caller left to fail.
        kafkaTemplate.send(topic, bet.jackpotId(), bet)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish bet {} to topic {}", bet.betId(), topic, ex);
                    } else {
                        log.info("Published bet {} to {}-{} (key={})",
                                bet.betId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                bet.jackpotId());
                    }
                });
    }
}

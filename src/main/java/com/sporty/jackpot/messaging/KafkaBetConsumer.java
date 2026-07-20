package com.sporty.jackpot.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.sporty.jackpot.service.JackpotContributionService;

/**
 * Consumes bets from the {@code jackpot-bets} topic. Active under the {@code kafka} profile.
 *
 * <p>Contains no business logic by design: it is a transport adapter and nothing else. All
 * contribution behaviour lives in {@link JackpotContributionService}, which the mock-profile
 * publisher calls too — so switching profiles changes how a bet arrives, never what happens to it.
 *
 * <p>The topic is read from the {@code jackpot.kafka.topic} property, the same one the producer
 * uses, so no Java file anywhere contains a literal topic name.
 */
@Component
@Profile("kafka")
public class KafkaBetConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaBetConsumer.class);

    private final JackpotContributionService contributionService;

    public KafkaBetConsumer(JackpotContributionService contributionService) {
        this.contributionService = contributionService;
    }

    @KafkaListener(topics = "${jackpot.kafka.topic}", groupId = "jackpot-service")
    public void onBet(BetMessage bet) {
        log.info("Received bet {} for jackpot '{}' from topic", bet.betId(), bet.jackpotId());
        contributionService.processBet(bet);
    }
}

package com.sporty.jackpot.config;

import java.util.Map;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import com.sporty.jackpot.messaging.BetMessage;

/**
 * Consumer wiring for the {@code kafka} profile.
 *
 * <p><b>Why this exists rather than a few properties in application.yml.</b> Phase 3 set
 * {@code spring.json.add.type.headers=false}, deliberately, so the payload is plain JSON that a
 * non-Java consumer can read. The consequence is that the deserializer has no type header to infer
 * a target type from and must be told the type explicitly. Expressing that in YAML would mean a
 * fully-qualified class name in a string, which no compiler checks and which fails at runtime if
 * {@code BetMessage} is ever moved or renamed. Constructing the deserializer here makes the target
 * type a compile-time reference instead.
 *
 * <p><b>{@link JacksonJsonDeserializer}, not the older {@code JsonDeserializer}.</b> It is the
 * Jackson 3 ({@code tools.jackson}) variant, matching the {@code JacksonJsonSerializer} on the
 * producer side. Both Jackson 2 and Jackson 3 happen to be on this classpath, so the old
 * deserializer would in fact run — but it would deserialize through a second, independently
 * configured Jackson 2 mapper, which is exactly the kind of silent asymmetry between producer and
 * consumer that later bites on a number or date format. One JSON stack on both ends.
 *
 * <p>Everything else — bootstrap servers, group id, offset reset — still comes from
 * {@link KafkaProperties}, so this class adds no second source of truth for broker configuration.
 */
@Configuration
@Profile("kafka")
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, BetMessage> betConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> consumerProperties = kafkaProperties.buildConsumerProperties();

        // false = do not consult type headers even if some producer sends them; this consumer
        // reads exactly one contract off this topic and the target type is not negotiable.
        JacksonJsonDeserializer<BetMessage> valueDeserializer =
                new JacksonJsonDeserializer<>(BetMessage.class, false);

        // Deserializer instances passed directly rather than as class names, so Kafka never has to
        // reflectively instantiate them and the configured target type cannot be lost.
        return new DefaultKafkaConsumerFactory<>(
                consumerProperties, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BetMessage> kafkaListenerContainerFactory(
            ConsumerFactory<String, BetMessage> betConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, BetMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(betConsumerFactory);
        return factory;
    }
}

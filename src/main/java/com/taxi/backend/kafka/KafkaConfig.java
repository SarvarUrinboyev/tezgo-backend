package com.taxi.backend.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");           // Ishonchlilik
        config.put(ProducerConfig.RETRIES_CONFIG, 3);            // Qayta urinish
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // Takrorlanmaslik
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }

    // Topic'lar yaratish
    @Bean
    public NewTopic topicEvents() {
        return TopicBuilder.name(TripEventProducer.TOPIC_EVENTS)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic topicCreated() {
        return TopicBuilder.name(TripEventProducer.TOPIC_CREATED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic topicCompleted() {
        return TopicBuilder.name(TripEventProducer.TOPIC_COMPLETED)
                .partitions(3).replicas(1).build();
    }
}

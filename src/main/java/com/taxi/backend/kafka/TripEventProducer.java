package com.taxi.backend.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Trip eventlarini Kafka'ga yuboruvchi servis.
 * kafka.enabled=true bo'lganda faollashadi.
 *
 * Topics:
 *   tezyol.trip.events   — barcha trip hodisalari
 *   tezyol.trip.created  — yangi buyurtmalar (haydovchilarga notification)
 *   tezyol.trip.completed — yakunlangan sayohatlar (analytics uchun)
 */
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class TripEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TripEventProducer.class);

    public static final String TOPIC_EVENTS    = "tezyol.trip.events";
    public static final String TOPIC_CREATED   = "tezyol.trip.created";
    public static final String TOPIC_COMPLETED = "tezyol.trip.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.enabled:false}")
    private boolean kafkaEnabled;

    public TripEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(TripEvent event) {
        if (!kafkaEnabled) return;

        String key = "trip-" + event.tripId();
        kafkaTemplate.send(TOPIC_EVENTS, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka] Trip event yuborilamadi: tripId={}", event.tripId(), ex);
                    } else {
                        log.debug("[Kafka] Event yuborildi: {} tripId={}", event.eventType(), event.tripId());
                    }
                });

        // Alohida topic'larga ham yuborish
        if ("TRIP_CREATED".equals(event.eventType())) {
            kafkaTemplate.send(TOPIC_CREATED, key, event);
        } else if ("TRIP_COMPLETED".equals(event.eventType())) {
            kafkaTemplate.send(TOPIC_COMPLETED, key, event);
        }
    }
}

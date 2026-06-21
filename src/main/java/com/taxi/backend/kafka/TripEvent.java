package com.taxi.backend.kafka;

/**
 * Trip voqeasi — Kafka topic'ga yuboriladigan event.
 * Barcha servislar shu eventni subscribe qilishi mumkin.
 */
public record TripEvent(
        String eventType,   // TRIP_CREATED, TRIP_ACCEPTED, TRIP_STARTED, TRIP_COMPLETED, TRIP_CANCELLED
        Long   tripId,
        Long   passengerId,
        Long   driverId,
        String status,
        long   price,
        double fromLat,
        double fromLon,
        String fromAddress,
        String toAddress,
        long   timestamp
) {
    public static TripEvent of(String type, Long tripId, Long passengerId, Long driverId,
                                String status, long price,
                                double fromLat, double fromLon,
                                String from, String to) {
        return new TripEvent(type, tripId, passengerId, driverId,
                status, price, fromLat, fromLon, from, to,
                System.currentTimeMillis());
    }
}

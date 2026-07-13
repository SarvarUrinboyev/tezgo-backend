package com.taxi.backend.enums;

/**
 * Durable lifecycle for a single driver's right to act on a SEARCHING trip.
 *
 * <p>Only PENDING_DELIVERY, ACTIVE and ACKNOWLEDGED are live states. The
 * partial PostgreSQL unique index in V49 enforces that a trip can have no more
 * than one live offer, even if two application nodes race.</p>
 */
public enum TripDriverOfferStatus {
    PENDING_DELIVERY,
    ACTIVE,
    ACKNOWLEDGED,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    DELIVERY_FAILED,
    CANCELLED,
    SUPERSEDED
}

package com.taxi.backend.model;

import com.taxi.backend.enums.TripDriverOfferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/** Durable, auditable offer ownership for sequential driver dispatch. */
@Entity
@Table(name = "trip_driver_offers")
public class TripDriverOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "generation", nullable = false)
    private int generation;

    @Column(name = "candidate_rank", nullable = false)
    private int candidateRank;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TripDriverOfferStatus status = TripDriverOfferStatus.PENDING_DELIVERY;

    @Column(name = "offered_at", nullable = false)
    private LocalDateTime offeredAt;

    /** Compatibility mirror of responseExpiresAt; null until the first outbound delivery attempt starts. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "first_delivery_attempt_at")
    private LocalDateTime firstDeliveryAttemptAt;

    @Column(name = "provider_accepted_at")
    private LocalDateTime providerAcceptedAt;

    /** Authoritative fixed deadline used by database state and the offerExpiresAt payload. */
    @Column(name = "response_expires_at")
    private LocalDateTime responseExpiresAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "delivery_attempt_state", nullable = false, length = 32)
    private String deliveryAttemptState = "NOT_QUEUED";

    @Column(name = "delivery_error", length = 500)
    private String deliveryError;

    @Column(name = "delivery_attempt_count", nullable = false)
    private int deliveryAttemptCount;

    @Column(name = "next_delivery_attempt_at")
    private LocalDateTime nextDeliveryAttemptAt;

    @Column(name = "last_delivery_outcome", length = 40)
    private String lastDeliveryOutcome;

    @Column(name = "delivery_recipient_fingerprint", length = 64)
    private String deliveryRecipientFingerprint;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; }
    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }
    public Driver getDriver() { return driver; }
    public void setDriver(Driver driver) { this.driver = driver; }
    public int getGeneration() { return generation; }
    public void setGeneration(int generation) { this.generation = generation; }
    public int getCandidateRank() { return candidateRank; }
    public void setCandidateRank(int candidateRank) { this.candidateRank = candidateRank; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    public TripDriverOfferStatus getStatus() { return status; }
    public void setStatus(TripDriverOfferStatus status) { this.status = status; }
    public LocalDateTime getOfferedAt() { return offeredAt; }
    public void setOfferedAt(LocalDateTime offeredAt) { this.offeredAt = offeredAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getFirstDeliveryAttemptAt() { return firstDeliveryAttemptAt; }
    public void setFirstDeliveryAttemptAt(LocalDateTime firstDeliveryAttemptAt) { this.firstDeliveryAttemptAt = firstDeliveryAttemptAt; }
    public LocalDateTime getProviderAcceptedAt() { return providerAcceptedAt; }
    public void setProviderAcceptedAt(LocalDateTime providerAcceptedAt) { this.providerAcceptedAt = providerAcceptedAt; }
    public LocalDateTime getResponseExpiresAt() { return responseExpiresAt; }
    public void setResponseExpiresAt(LocalDateTime responseExpiresAt) { this.responseExpiresAt = responseExpiresAt; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public String getDeliveryAttemptState() { return deliveryAttemptState; }
    public void setDeliveryAttemptState(String deliveryAttemptState) { this.deliveryAttemptState = deliveryAttemptState; }
    public String getDeliveryError() { return deliveryError; }
    public void setDeliveryError(String deliveryError) { this.deliveryError = deliveryError; }
    public int getDeliveryAttemptCount() { return deliveryAttemptCount; }
    public void setDeliveryAttemptCount(int deliveryAttemptCount) { this.deliveryAttemptCount = deliveryAttemptCount; }
    public LocalDateTime getNextDeliveryAttemptAt() { return nextDeliveryAttemptAt; }
    public void setNextDeliveryAttemptAt(LocalDateTime nextDeliveryAttemptAt) { this.nextDeliveryAttemptAt = nextDeliveryAttemptAt; }
    public String getLastDeliveryOutcome() { return lastDeliveryOutcome; }
    public void setLastDeliveryOutcome(String lastDeliveryOutcome) { this.lastDeliveryOutcome = lastDeliveryOutcome; }
    public String getDeliveryRecipientFingerprint() { return deliveryRecipientFingerprint; }
    public void setDeliveryRecipientFingerprint(String deliveryRecipientFingerprint) { this.deliveryRecipientFingerprint = deliveryRecipientFingerprint; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

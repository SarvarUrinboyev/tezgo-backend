package com.taxi.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Durable ownership/result of one operator trip-create HTTP request. */
@Entity
@Table(name = "operator_trip_idempotencies")
public class OperatorTripIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @Column(name = "idempotency_key", nullable = false, length = 36)
    private String idempotencyKey;

    /** SHA-256 digest; V47 intentionally persists it as fixed-width CHAR(64). */
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String requestHash;

    @Column(nullable = false, length = 20)
    private String state = "PROCESSING";

    @ManyToOne
    @JoinColumn(name = "trip_id")
    private Trip trip;

    /** PII-free original success metadata, immutable once the create completes. */
    @Column(name = "response_status", length = 40)
    private String responseStatus;

    @Column(name = "response_source", length = 20)
    private String responseSource;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getOperator() { return operator; }
    public void setOperator(User operator) { this.operator = operator; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }
    public String getResponseStatus() { return responseStatus; }
    public void setResponseStatus(String responseStatus) { this.responseStatus = responseStatus; }
    public String getResponseSource() { return responseSource; }
    public void setResponseSource(String responseSource) { this.responseSource = responseSource; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

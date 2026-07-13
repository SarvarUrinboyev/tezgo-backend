package com.taxi.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Durable ownership and state for a driver-created Click payment link.
 * Redis may cache this data but must never be its only copy.
 */
@Entity
@Table(name = "click_payment_orders")
public class ClickPaymentOrder {

    @Id
    @Column(name = "merchant_trans_id", nullable = false, length = 64)
    private String merchantTransId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    /** Tiyinlarda. Click callback amount is converted from UZS before comparing. */
    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 20)
    private String status = "CREATED";

    @Column(name = "click_trans_id", unique = true, length = 64)
    private String clickTransId;

    @Column(name = "click_paydoc_id", unique = true, length = 64)
    private String clickPaydocId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public String getMerchantTransId() { return merchantTransId; }
    public void setMerchantTransId(String merchantTransId) { this.merchantTransId = merchantTransId; }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getClickTransId() { return clickTransId; }
    public void setClickTransId(String clickTransId) { this.clickTransId = clickTransId; }

    public String getClickPaydocId() { return clickPaydocId; }
    public void setClickPaydocId(String clickPaydocId) { this.clickPaydocId = clickPaydocId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

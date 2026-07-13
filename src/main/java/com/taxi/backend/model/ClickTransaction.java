package com.taxi.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Click to'lov callback'lari uchun DURABLE (bardoshli) idempotency ledgeri.
 *
 * UNIQUE(click_trans_id) + status'ning atomik PREPARED->CONFIRMED o'tishi — Redis'dan
 * MUSTAQIL ravishda bir click_trans_id ni ikki marta kreditlashni (double-credit) to'sadi.
 * Pulni kreditlash o'zi {@code transactions} jadvali + {@code PaymentService.creditDriverBalance}
 * orqali bo'ladi (Payme va admin to'ldirish bilan bir xil yo'l) — bu jadval faqat
 * "shu click_trans_id allaqachon ishlanganmi?" degan savolga bardoshli javob beradi.
 *
 * Ustun turlari V41 migratsiyasiga AYNAN mos (Hibernate ddl-auto=validate uchun).
 */
@Entity
@Table(name = "click_transactions")
public class ClickTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "click_trans_id", nullable = false, unique = true, length = 64)
    private String clickTransId;

    /** Shop API payment document identifier, retained for reconciliation. */
    @Column(name = "click_paydoc_id", unique = true, length = 64)
    private String clickPaydocId;

    @Column(name = "merchant_trans_id", nullable = false, length = 64)
    private String merchantTransId;

    @Column(name = "driver_id")
    private Long driverId;

    /** Tiyinlarda (drivers.balance / transactions.amount bilan bir xil birlik). */
    @Column(nullable = false)
    private Long amount;

    /** 0 = PREPARE, 1 = COMPLETE. */
    @Column(nullable = false)
    private Integer action = 0;

    /** PREPARED | CONFIRMED | CANCELLED. */
    @Column(nullable = false, length = 20)
    private String status = "PREPARED";

    @Column(name = "merchant_prepare_id", length = 64)
    private String merchantPrepareId;

    @Column(name = "merchant_confirm_id", length = 64)
    private String merchantConfirmId;

    @Column(nullable = false)
    private Integer error = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public ClickTransaction() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClickTransId() { return clickTransId; }
    public void setClickTransId(String clickTransId) { this.clickTransId = clickTransId; }

    public String getClickPaydocId() { return clickPaydocId; }
    public void setClickPaydocId(String clickPaydocId) { this.clickPaydocId = clickPaydocId; }

    public String getMerchantTransId() { return merchantTransId; }
    public void setMerchantTransId(String merchantTransId) { this.merchantTransId = merchantTransId; }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }

    public Integer getAction() { return action; }
    public void setAction(Integer action) { this.action = action; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMerchantPrepareId() { return merchantPrepareId; }
    public void setMerchantPrepareId(String merchantPrepareId) { this.merchantPrepareId = merchantPrepareId; }

    public String getMerchantConfirmId() { return merchantConfirmId; }
    public void setMerchantConfirmId(String merchantConfirmId) { this.merchantConfirmId = merchantConfirmId; }

    public Integer getError() { return error; }
    public void setError(Integer error) { this.error = error; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}

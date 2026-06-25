package com.taxi.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Click ADVANCED SHOP callback'lari uchun DURABLE idempotency ledgeri.
 *
 * Merchant API'ning {@link ClickTransaction} ledgeridan ALOHIDA jadval ({@code click_shop_transactions},
 * V42) — ikkala integratsiya bir-biriga TEGMAYDI. Pulni kreditlash bu yerda EMAS; bu jadval faqat
 * "shu click_paydoc_id allaqachon ishlanganmi?" degan savolga bardoshli javob beradi.
 *
 * Idempotentlikning yuragi — UNIQUE(click_paydoc_id) + status'ning shartli PREPARED -&gt; CONFIRMED
 * o'tishi (Postgres row-level lock). Bir vaqtda kelgan ikki COMPLETE'dan FAQAT bittasi g'olib
 * (rowcount=1) bo'ladi va kreditlaydi; qolgani 0 oladi va idempotent o'tib ketadi.
 */
@Entity
@Table(name = "click_shop_transactions")
public class ClickShopTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "click_paydoc_id", nullable = false, unique = true, length = 64)
    private String clickPaydocId;

    @Column(name = "attempt_trans_id", length = 64)
    private String attemptTransId;

    @Column(name = "driver_id")
    private Long driverId;

    /** Tiyinlarda (drivers.balance / transactions.amount bilan bir xil birlik). */
    @Column(nullable = false)
    private Long amount;

    /** 1 = PREPARE, 2 = COMPLETE (ADVANCED SHOP codes; Merchant API 0/1 dan farqli). */
    @Column(nullable = false)
    private Integer action = 1;

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

    public ClickShopTransaction() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClickPaydocId() { return clickPaydocId; }
    public void setClickPaydocId(String clickPaydocId) { this.clickPaydocId = clickPaydocId; }

    public String getAttemptTransId() { return attemptTransId; }
    public void setAttemptTransId(String attemptTransId) { this.attemptTransId = attemptTransId; }

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

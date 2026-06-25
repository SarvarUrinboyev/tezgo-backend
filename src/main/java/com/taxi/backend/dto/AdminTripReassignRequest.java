package com.taxi.backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Admin — SEARCHING buyurtmani aniq haydovchiga yo'naltirish.
 * Push xabari MUTLAQO mavjud dispatch yo'lidan o'tadi (PushNotificationService.notifyDriver,
 * data-only, type=NEW_ORDER + ORDER_PUSH). Yangi push yo'li yaratilmaydi.
 */
public class AdminTripReassignRequest {
    /** Driver primary key (numeric). Admin panel resolved driver_code (TZ-XXXX) to this id before submitting. */
    @NotNull(message = "driverId kerak")
    private Long driverId;

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
}

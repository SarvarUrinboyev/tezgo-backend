package com.taxi.backend.service;

import java.util.Objects;

/**
 * Sanitized outcome returned by the approved order-push contract.
 * A successful provider hand-off is deliberately not treated as device receipt.
 */
public record OrderPushDeliveryOutcome(
        OrderPushDeliveryOutcomeCategory category,
        String providerCode,
        String providerMessageId,
        String recipientFingerprint,
        boolean recipientStillCurrent,
        boolean providerAccepted) {

    public OrderPushDeliveryOutcome {
        Objects.requireNonNull(category, "category");
    }

    public boolean isPermanentRecipientFailure() {
        return category == OrderPushDeliveryOutcomeCategory.PERMANENT_RECIPIENT_FAILURE;
    }

    public OrderPushDeliveryOutcome withRecipientStillCurrent(boolean stillCurrent) {
        return new OrderPushDeliveryOutcome(category, providerCode, providerMessageId,
                recipientFingerprint, stillCurrent, providerAccepted);
    }
}

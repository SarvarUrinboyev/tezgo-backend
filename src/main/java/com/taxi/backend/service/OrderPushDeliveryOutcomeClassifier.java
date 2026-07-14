package com.taxi.backend.service;

import com.google.firebase.messaging.MessagingErrorCode;

/** Typed Firebase classification. No human-readable exception text is inspected. */
final class OrderPushDeliveryOutcomeClassifier {

    private OrderPushDeliveryOutcomeClassifier() {
    }

    static OrderPushDeliveryOutcomeCategory classify(MessagingErrorCode code) {
        if (code == null) return OrderPushDeliveryOutcomeCategory.UNKNOWN_FAILURE;
        return switch (code) {
            case UNREGISTERED -> OrderPushDeliveryOutcomeCategory.PERMANENT_RECIPIENT_FAILURE;
            case UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED -> OrderPushDeliveryOutcomeCategory.TRANSIENT_FAILURE;
            case SENDER_ID_MISMATCH, THIRD_PARTY_AUTH_ERROR, INVALID_ARGUMENT ->
                    OrderPushDeliveryOutcomeCategory.GLOBAL_CONFIGURATION_FAILURE;
        };
    }
}

package com.taxi.backend.service;

import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPushDeliveryOutcomeClassifierTest {

    @Test
    void usesOnlyTypedFirebaseCodesWithConservativeCategories() {
        assertThat(OrderPushDeliveryOutcomeClassifier.classify(MessagingErrorCode.UNREGISTERED))
                .isEqualTo(OrderPushDeliveryOutcomeCategory.PERMANENT_RECIPIENT_FAILURE);
        assertThat(OrderPushDeliveryOutcomeClassifier.classify(MessagingErrorCode.UNAVAILABLE))
                .isEqualTo(OrderPushDeliveryOutcomeCategory.TRANSIENT_FAILURE);
        assertThat(OrderPushDeliveryOutcomeClassifier.classify(MessagingErrorCode.INTERNAL))
                .isEqualTo(OrderPushDeliveryOutcomeCategory.TRANSIENT_FAILURE);
        assertThat(OrderPushDeliveryOutcomeClassifier.classify(MessagingErrorCode.QUOTA_EXCEEDED))
                .isEqualTo(OrderPushDeliveryOutcomeCategory.TRANSIENT_FAILURE);
        assertThat(OrderPushDeliveryOutcomeClassifier.classify(MessagingErrorCode.SENDER_ID_MISMATCH))
                .isEqualTo(OrderPushDeliveryOutcomeCategory.GLOBAL_CONFIGURATION_FAILURE);
        assertThat(OrderPushDeliveryOutcomeClassifier.classify(MessagingErrorCode.THIRD_PARTY_AUTH_ERROR))
                .isEqualTo(OrderPushDeliveryOutcomeCategory.GLOBAL_CONFIGURATION_FAILURE);
        assertThat(OrderPushDeliveryOutcomeClassifier.classify(MessagingErrorCode.INVALID_ARGUMENT))
                .isEqualTo(OrderPushDeliveryOutcomeCategory.GLOBAL_CONFIGURATION_FAILURE);
        assertThat(OrderPushDeliveryOutcomeClassifier.classify(null))
                .isEqualTo(OrderPushDeliveryOutcomeCategory.UNKNOWN_FAILURE);
    }
}

package com.taxi.backend.service;

/** Provider-level result only; it never claims that a device displayed an order. */
public enum OrderPushDeliveryOutcomeCategory {
    SUCCESS,
    PERMANENT_RECIPIENT_FAILURE,
    TRANSIENT_FAILURE,
    UNKNOWN_FAILURE,
    GLOBAL_CONFIGURATION_FAILURE,
    UNSUPPORTED_DELIVERY_PATH
}

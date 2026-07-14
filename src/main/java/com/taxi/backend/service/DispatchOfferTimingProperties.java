package com.taxi.backend.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/** Explicit, separately configurable clocks used by the sequential dispatch handoff. */
@Component
public class DispatchOfferTimingProperties {

    @Value("${app.dispatch.order-fcm-transport-ttl-seconds:5}")
    private long orderFcmTransportTtlSeconds = 5;

    @Value("${app.dispatch.native-alarm-duration-seconds:15}")
    private long nativeAlarmDurationSeconds = 15;

    @Value("${app.dispatch.provider-rpc-budget-millis:1000}")
    private long providerRpcBudgetMillis = 1_000;

    @Value("${app.dispatch.clock-safety-margin-millis:1000}")
    private long clockSafetyMarginMillis = 1_000;

    @Value("${app.dispatch.transient-retry-backoff-millis:2000}")
    private long transientRetryBackoffMillis = 2_000;

    @Value("${app.dispatch.max-delivery-attempts:1}")
    private int maxDeliveryAttempts = 1;

    @PostConstruct
    void validate() {
        if (orderFcmTransportTtlSeconds <= 0 || orderFcmTransportTtlSeconds > 2_419_200) {
            throw new IllegalStateException("app.dispatch.order-fcm-transport-ttl-seconds must be 1..2419200");
        }
        if (nativeAlarmDurationSeconds <= 0 || nativeAlarmDurationSeconds > 300
                || providerRpcBudgetMillis <= 0 || providerRpcBudgetMillis > 60_000
                || clockSafetyMarginMillis <= 0 || clockSafetyMarginMillis > 60_000
                || transientRetryBackoffMillis <= 0 || transientRetryBackoffMillis > 60_000
                || maxDeliveryAttempts <= 0 || maxDeliveryAttempts > 3) {
            throw new IllegalStateException("invalid AO-P1-03 dispatch timing configuration");
        }
    }

    public LocalDateTime safeResponseDeadline(LocalDateTime firstDeliveryAttemptStartedAt) {
        return firstDeliveryAttemptStartedAt
                .plus(Duration.ofMillis(providerRpcBudgetMillis))
                .plusSeconds(orderFcmTransportTtlSeconds)
                .plusSeconds(nativeAlarmDurationSeconds)
                .plus(Duration.ofMillis(clockSafetyMarginMillis));
    }

    public boolean canStartAnotherDeliveryAttempt(LocalDateTime attemptStart, LocalDateTime safeDeadline) {
        return !safeResponseDeadline(attemptStart).isAfter(safeDeadline);
    }

    public Duration orderFcmTransportTtl() { return Duration.ofSeconds(orderFcmTransportTtlSeconds); }
    public Duration transientRetryBackoff() { return Duration.ofMillis(transientRetryBackoffMillis); }
    public int maxDeliveryAttempts() { return maxDeliveryAttempts; }
    public long orderFcmTransportTtlSeconds() { return orderFcmTransportTtlSeconds; }
    public long nativeAlarmDurationSeconds() { return nativeAlarmDurationSeconds; }
    public long providerRpcBudgetMillis() { return providerRpcBudgetMillis; }
    public long clockSafetyMarginMillis() { return clockSafetyMarginMillis; }
    public long transientRetryBackoffMillis() { return transientRetryBackoffMillis; }
}

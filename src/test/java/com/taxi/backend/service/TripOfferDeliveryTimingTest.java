package com.taxi.backend.service;

import com.taxi.backend.enums.TripDriverOfferStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.TripDriverOffer;
import com.taxi.backend.repository.TripDriverOfferRepository;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripOfferDeliveryTimingTest {

    @Mock private TripDriverOfferRepository offers;
    @Mock private TripRepository trips;
    @Mock private SimpMessagingTemplate messaging;
    @Mock private PushNotificationService pushes;
    @Mock private ObjectProvider<TripOfferLifecycleService> lifecycle;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-14T08:00:00Z"), ZoneOffset.UTC);

    @Test
    @SuppressWarnings("unchecked")
    void firstDeliveryPersistsAndPublishesOneMatchingFixedSafeDeadline() {
        DispatchOfferTimingProperties timing = timing(5, 15, 1_000, 1_000, 2_000, 1);
        Trip trip = searchingTrip();
        TripDriverOffer offer = pendingOffer(trip);
        when(offers.findById(1L)).thenReturn(Optional.of(offer));
        when(offers.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(trips.findByIdForUpdate(55L)).thenReturn(Optional.of(trip));
        when(pushes.sendOrderPushWithOutcome(eq(9L), any())).thenReturn(success());

        new TripOfferDeliveryService(offers, trips, messaging, pushes, lifecycle, timing, clock)
                .deliverOfferAsync(1L);

        LocalDateTime start = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDateTime expectedDeadline = start.plusSeconds(22);
        assertThat(offer.getFirstDeliveryAttemptAt()).isEqualTo(start);
        assertThat(offer.getResponseExpiresAt()).isEqualTo(expectedDeadline);
        assertThat(offer.getExpiresAt()).isEqualTo(expectedDeadline);
        assertThat(offer.getProviderAcceptedAt()).isEqualTo(start);
        assertThat(offer.getStatus()).isEqualTo(TripDriverOfferStatus.ACTIVE);
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(pushes).sendOrderPushWithOutcome(eq(9L), payload.capture());
        long expectedEpochMillis = expectedDeadline.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertThat(payload.getValue()).containsEntry("offerExpiresAt", expectedEpochMillis)
                .containsEntry("type", "ORDER_PUSH").containsEntry("event", "ORDER_PUSH");
        verify(offers).save(offer);
    }

    @Test
    void unknownResultPreservesCurrentOwnerAndNeverCreatesAnotherOffer() {
        Trip trip = searchingTrip();
        TripDriverOffer offer = pendingOffer(trip);
        when(offers.findById(1L)).thenReturn(Optional.of(offer));
        when(offers.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(trips.findByIdForUpdate(55L)).thenReturn(Optional.of(trip));
        when(pushes.sendOrderPushWithOutcome(eq(9L), any())).thenReturn(new OrderPushDeliveryOutcome(
                OrderPushDeliveryOutcomeCategory.UNKNOWN_FAILURE, "SocketTimeoutException", null, "fp", true, false));

        new TripOfferDeliveryService(offers, trips, messaging, pushes, lifecycle, new DispatchOfferTimingProperties(), clock)
                .deliverOfferAsync(1L);

        assertThat(offer.getStatus()).isEqualTo(TripDriverOfferStatus.PENDING_DELIVERY);
        assertThat(offer.getDeliveryAttemptState()).isEqualTo("UNKNOWN_FAILURE");
        verify(lifecycle, never()).getObject();
    }

    @Test
    void unregisteredDelegatesOnlyWhenTheFailedRecipientIsStillCurrent() {
        Trip trip = searchingTrip();
        TripDriverOffer offer = pendingOffer(trip);
        when(offers.findById(1L)).thenReturn(Optional.of(offer));
        when(offers.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(trips.findByIdForUpdate(55L)).thenReturn(Optional.of(trip));
        TripOfferLifecycleService lifecycleService = org.mockito.Mockito.mock(TripOfferLifecycleService.class);
        when(lifecycle.getObject()).thenReturn(lifecycleService);
        OrderPushDeliveryOutcome outcome = new OrderPushDeliveryOutcome(
                OrderPushDeliveryOutcomeCategory.PERMANENT_RECIPIENT_FAILURE, "UNREGISTERED", null, "fp", true, false);
        when(pushes.sendOrderPushWithOutcome(eq(9L), any())).thenReturn(outcome);

        new TripOfferDeliveryService(offers, trips, messaging, pushes, lifecycle, new DispatchOfferTimingProperties(), clock)
                .deliverOfferAsync(1L);

        assertThat(offer.getDeliveryRecipientFingerprint()).isEqualTo("fp");
        verify(lifecycleService).handlePermanentRecipientFailure(1L, outcome);
    }

    private static DispatchOfferTimingProperties timing(long fcmTtlSeconds, long nativeSeconds,
                                                          long rpcMillis, long marginMillis,
                                                          long backoffMillis, int attempts) {
        DispatchOfferTimingProperties timing = new DispatchOfferTimingProperties();
        ReflectionTestUtils.setField(timing, "orderFcmTransportTtlSeconds", fcmTtlSeconds);
        ReflectionTestUtils.setField(timing, "nativeAlarmDurationSeconds", nativeSeconds);
        ReflectionTestUtils.setField(timing, "providerRpcBudgetMillis", rpcMillis);
        ReflectionTestUtils.setField(timing, "clockSafetyMarginMillis", marginMillis);
        ReflectionTestUtils.setField(timing, "transientRetryBackoffMillis", backoffMillis);
        ReflectionTestUtils.setField(timing, "maxDeliveryAttempts", attempts);
        return timing;
    }

    private static Trip searchingTrip() {
        Trip trip = new Trip();
        trip.setId(55L); trip.setStatus(TripStatus.SEARCHING); trip.setFromAddress("A"); trip.setToAddress("B");
        trip.setFromLat(41.2); trip.setFromLon(69.2); trip.setToLat(41.3); trip.setToLon(69.3); trip.setTotalPrice(100_000L);
        return trip;
    }

    private static TripDriverOffer pendingOffer(Trip trip) {
        Driver driver = new Driver(); driver.setId(9L);
        TripDriverOffer offer = new TripDriverOffer();
        offer.setId(1L); offer.setTrip(trip); offer.setDriver(driver); offer.setStatus(TripDriverOfferStatus.PENDING_DELIVERY);
        offer.setDistanceKm(1.0d); offer.setDeliveryAttemptCount(0);
        return offer;
    }

    private static OrderPushDeliveryOutcome success() {
        return new OrderPushDeliveryOutcome(OrderPushDeliveryOutcomeCategory.SUCCESS,
                null, "provider-id", "fp", true, true);
    }
}

package com.taxi.backend.service;

import com.taxi.backend.enums.TripDriverOfferStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.TripDriverOffer;
import com.taxi.backend.repository.TripDriverOfferRepository;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Frozen mobile contract regression: delivery is one owner, one unchanged payload pair. */
@ExtendWith(MockitoExtension.class)
class TripOfferDeliveryServiceTest {
    @Mock private TripDriverOfferRepository offers;
    @Mock private TripRepository trips;
    @Mock private SimpMessagingTemplate messaging;
    @Mock private PushNotificationService pushes;

    @Test
    @SuppressWarnings("unchecked")
    void activeOfferDeliversMatchingWsAndDataOnlyPushToItsSingleOwner() {
        Trip trip = new Trip();
        trip.setId(55L); trip.setFromAddress("A"); trip.setToAddress("B");
        trip.setStatus(TripStatus.SEARCHING);
        trip.setFromLat(41.2); trip.setFromLon(69.2); trip.setToLat(41.3); trip.setToLon(69.3);
        trip.setTotalPrice(100_000L);
        Tariff tariff = new Tariff(); tariff.setName("STANDART"); tariff.setBasePrice(20_000L); trip.setTariff(tariff);
        Driver driver = new Driver(); driver.setId(9L);
        TripDriverOffer offer = new TripDriverOffer();
        offer.setId(1L); offer.setTrip(trip); offer.setDriver(driver); offer.setDistanceKm(1.0d);
        offer.setStatus(TripDriverOfferStatus.PENDING_DELIVERY);
        LocalDateTime expiry = LocalDateTime.now().plusSeconds(15);
        offer.setExpiresAt(expiry);
        when(offers.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(trips.findByIdForUpdate(55L)).thenReturn(Optional.of(trip));

        new TripOfferDeliveryService(offers, trips, messaging, pushes).deliverOfferAsync(1L);

        ArgumentCaptor<Map<String, Object>> ws = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> push = ArgumentCaptor.forClass(Map.class);
        verify(messaging).convertAndSend(eq("/topic/driver/9"), ws.capture());
        verify(pushes).notifyDriver(eq(9L), eq(null), eq(null), push.capture());
        assertThat(ws.getValue()).containsEntry("type", "NEW_ORDER").containsEntry("tripId", 55L);
        assertThat(push.getValue()).containsEntry("type", "ORDER_PUSH").containsEntry("event", "ORDER_PUSH")
                .containsEntry("tripId", 55L);
        long expectedExpiry = expiry.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertThat(ws.getValue().get("offerExpiresAt")).isEqualTo(expectedExpiry);
        assertThat(push.getValue().get("offerExpiresAt")).isEqualTo(expectedExpiry);
        assertThat(offer.getStatus()).isEqualTo(TripDriverOfferStatus.ACTIVE);
        assertThat(offer.getDeliveryAttemptState()).isEqualTo("ATTEMPTED");
        verify(offers).save(offer);
    }

    @Test
    void acceptedTripAtDeliveryTimeNeverSendsStaleOrder() {
        Trip trip = new Trip();
        trip.setId(55L); trip.setStatus(TripStatus.ACCEPTED);
        Driver driver = new Driver(); driver.setId(9L); trip.setDriver(driver);
        TripDriverOffer offer = new TripDriverOffer();
        offer.setId(1L); offer.setTrip(trip); offer.setDriver(driver);
        offer.setStatus(TripDriverOfferStatus.PENDING_DELIVERY);
        offer.setExpiresAt(LocalDateTime.now().plusSeconds(15));
        when(offers.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(trips.findByIdForUpdate(55L)).thenReturn(Optional.of(trip));

        new TripOfferDeliveryService(offers, trips, messaging, pushes).deliverOfferAsync(1L);

        verifyNoInteractions(messaging, pushes);
        assertThat(offer.getStatus()).isEqualTo(TripDriverOfferStatus.ACCEPTED);
        assertThat(offer.getDeliveryAttemptState()).isEqualTo("SKIPPED_CLOSED");
        verify(offers).save(offer);
    }
}

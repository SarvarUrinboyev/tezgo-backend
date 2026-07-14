package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.TripDriverOfferStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.TripDriverOffer;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripDriverOfferRepository;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** AO-P1-03 regression: ranked candidates never become an initial fan-out. */
@ExtendWith(MockitoExtension.class)
class TripNotificationHelperSequentialDispatchRegressionTest {

    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private TripDriverOfferRepository offerRepository;
    @Mock private MatchingService matchingService;
    @Mock private TripOfferDeliveryService deliveryService;

    private TripOfferLifecycleService lifecycle;
    private Trip trip;

    @BeforeEach
    void setUp() {
        lifecycle = new TripOfferLifecycleService(tripRepository, driverRepository, offerRepository,
                matchingService, deliveryService);
        ReflectionTestUtils.setField(lifecycle, "matchingRadiusKm", 5.0d);
        ReflectionTestUtils.setField(lifecycle, "offerTtlSeconds", 15L);
        trip = trip(704L);
        org.mockito.Mockito.lenient().when(tripRepository.findByIdForUpdate(704L)).thenReturn(Optional.of(trip));
        org.mockito.Mockito.lenient().when(offerRepository.findLiveByTripIdForUpdate(anyLong(), any())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(offerRepository.findAllOfferedDriverIdsByTripId(704L)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(offerRepository.findMaxGenerationByTripId(704L)).thenReturn(0);
        org.mockito.Mockito.lenient().when(tripRepository.findBusyDriverIds(TripStatus.ACTIVE_DRIVER_STATUSES)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(driverRepository.findEnabledServiceTypesByDriverId(anyLong())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(offerRepository.saveAndFlush(any(TripDriverOffer.class))).thenAnswer(invocation -> {
            TripDriverOffer offer = invocation.getArgument(0);
            offer.setId(901L);
            return offer;
        });
    }

    @Test
    void sixRankedEligibleDrivers_onlyRankOneReceivesInitialOffer() {
        when(matchingService.findNearbyDrivers(anyDouble(), anyDouble(), anyDouble(), anyBoolean()))
                .thenReturn(ranked(1L, 2L, 3L, 4L, 5L, 6L));
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));

        lifecycle.dispatchNextOffer(704L);

        ArgumentCaptor<TripDriverOffer> saved = ArgumentCaptor.forClass(TripDriverOffer.class);
        verify(offerRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDriver().getId()).isEqualTo(1L);
        assertThat(saved.getValue().getCandidateRank()).isEqualTo(1);
        verify(driverRepository, never()).findById(2L);
        verify(deliveryService).deliverOfferAsync(901L);
    }

    @Test
    void fewerThanThreeCandidates_doesNotUseOnlineFallback() {
        when(matchingService.findNearbyDrivers(anyDouble(), anyDouble(), anyDouble(), anyBoolean()))
                .thenReturn(ranked(1L));
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver(1L)));

        lifecycle.dispatchNextOffer(704L);

        verify(driverRepository, never()).findByIsOnlineTrue();
        verify(offerRepository).saveAndFlush(any(TripDriverOffer.class));
    }

    @Test
    void nonOwnerCannotAcknowledgeOrAcceptCurrentOffer() {
        Driver owner = driver(1L);
        TripDriverOffer offer = new TripDriverOffer();
        offer.setId(77L); offer.setTrip(trip); offer.setDriver(owner);
        offer.setStatus(com.taxi.backend.enums.TripDriverOfferStatus.ACTIVE);
        offer.setExpiresAt(LocalDateTime.now().plusSeconds(15));
        when(offerRepository.findLiveByTripIdForUpdate(org.mockito.ArgumentMatchers.eq(704L), any())).thenReturn(List.of(offer));

        assertFalse(lifecycle.acknowledgeCurrentOffer(704L, 2L));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> lifecycle.requireCurrentOfferForAcceptance(704L, 2L));
        assertTrue(error.getReason().contains("taklif qilinmagan"));
    }

    @Test
    void currentOwnerRejects_rankTwoBecomesTheOnlyNextOffer() {
        Driver first = driver(1L);
        TripDriverOffer active = offer(77L, first, TripDriverOfferStatus.ACTIVE,
                LocalDateTime.now().plusSeconds(15));
        when(offerRepository.findLiveByTripIdForUpdate(org.mockito.ArgumentMatchers.eq(704L), any()))
                .thenReturn(List.of(active));
        when(offerRepository.findAllOfferedDriverIdsByTripId(704L)).thenReturn(List.of(1L));
        when(offerRepository.findMaxGenerationByTripId(704L)).thenReturn(1);
        when(matchingService.findNearbyDrivers(anyDouble(), anyDouble(), anyDouble(), anyBoolean()))
                .thenReturn(ranked(1L, 2L));
        when(driverRepository.findById(2L)).thenReturn(Optional.of(driver(2L)));
        when(offerRepository.saveAndFlush(any(TripDriverOffer.class))).thenAnswer(invocation -> {
            TripDriverOffer next = invocation.getArgument(0);
            next.setId(902L);
            return next;
        });

        lifecycle.rejectCurrentOfferAndDispatchNext(704L, 1L);

        assertThat(active.getStatus()).isEqualTo(com.taxi.backend.enums.TripDriverOfferStatus.REJECTED);
        ArgumentCaptor<TripDriverOffer> saved = ArgumentCaptor.forClass(TripDriverOffer.class);
        verify(offerRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getGeneration()).isEqualTo(2);
        assertThat(saved.getValue().getCandidateRank()).isEqualTo(2);
        assertThat(saved.getValue().getDriver().getId()).isEqualTo(2L);
        verify(deliveryService).deliverOfferAsync(902L);
        verify(driverRepository, never()).findById(1L);
    }

    @Test
    void expiredOffer_advancesOnceToNextRankedDriver() {
        Driver first = driver(1L);
        TripDriverOffer expired = offer(77L, first, TripDriverOfferStatus.ACKNOWLEDGED,
                LocalDateTime.now().minusSeconds(1));
        when(offerRepository.findById(77L)).thenReturn(Optional.of(expired));
        when(offerRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(expired));
        when(offerRepository.findAllOfferedDriverIdsByTripId(704L)).thenReturn(List.of(1L));
        when(offerRepository.findMaxGenerationByTripId(704L)).thenReturn(1);
        when(matchingService.findNearbyDrivers(anyDouble(), anyDouble(), anyDouble(), anyBoolean()))
                .thenReturn(ranked(1L, 2L));
        when(driverRepository.findById(2L)).thenReturn(Optional.of(driver(2L)));
        when(offerRepository.saveAndFlush(any(TripDriverOffer.class))).thenAnswer(invocation -> {
            TripDriverOffer next = invocation.getArgument(0);
            next.setId(903L);
            return next;
        });

        lifecycle.expireOfferAndDispatchNext(77L);

        assertThat(expired.getStatus()).isEqualTo(com.taxi.backend.enums.TripDriverOfferStatus.EXPIRED);
        ArgumentCaptor<TripDriverOffer> saved = ArgumentCaptor.forClass(TripDriverOffer.class);
        verify(offerRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getGeneration()).isEqualTo(2);
        assertThat(saved.getValue().getDriver().getId()).isEqualTo(2L);
        verify(deliveryService).deliverOfferAsync(903L);
    }

    @Test
    void restartRecoveryRetriesOnlyPersistedPendingOfferOwners() {
        when(offerRepository.findPendingDeliveryOfferIds(any())).thenReturn(List.of(901L, 902L));

        lifecycle.recoverPendingOfferDeliveries();

        verify(deliveryService).deliverOfferAsync(901L);
        verify(deliveryService).deliverOfferAsync(902L);
        verifyNoMoreInteractions(deliveryService);
    }

    private static Trip trip(long id) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setStatus(TripStatus.SEARCHING);
        trip.setFromLat(41.30d);
        trip.setFromLon(69.60d);
        trip.setTotalPrice(1_000_000L);
        return trip;
    }

    private static Driver driver(long id) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setStatus(DriverStatus.ACTIVE);
        driver.setOnline(true);
        driver.setCarModel("Cobalt");
        driver.setBalance(0L);
        return driver;
    }

    private TripDriverOffer offer(long id, Driver owner,
                                  com.taxi.backend.enums.TripDriverOfferStatus status,
                                  LocalDateTime expiresAt) {
        TripDriverOffer offer = new TripDriverOffer();
        offer.setId(id);
        offer.setTrip(trip);
        offer.setDriver(owner);
        offer.setGeneration(1);
        offer.setCandidateRank(1);
        offer.setStatus(status);
        offer.setExpiresAt(expiresAt);
        return offer;
    }

    private static List<MatchingService.MatchedDriver> ranked(long... ids) {
        List<MatchingService.MatchedDriver> drivers = new ArrayList<>();
        for (int index = 0; index < ids.length; index++) {
            long id = ids[index];
            drivers.add(new MatchingService.MatchedDriver(id, "driver-" + id, "Cobalt", "01A" + id,
                    41.30d, 69.60d, 1.0d + index, 2.0d + index,
                    5.0d, "STANDART", null, 0.0d, null));
        }
        return drivers;
    }
}

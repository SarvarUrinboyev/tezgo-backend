package com.taxi.backend.service;

import com.taxi.backend.dto.OperatorTripRequest;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.exception.ConflictException;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.User;
import com.taxi.backend.pricing.NightFareService;
import com.taxi.backend.pricing.SurgeResult;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;

/** Regression: a caller with a non-terminal trip cannot receive a second operator dispatch. */
@ExtendWith(MockitoExtension.class)
class OperatorTripCreateGuardTest {

    @Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private TariffRepository tariffRepository;
    @Mock private SurgePricingService surgePricingService;
    @Mock private TripNotificationHelper notificationHelper;
    @Mock private SecurityMonitorService securityMonitor;

    private OperatorService service;
    private User operator;
    private User passenger;

    @BeforeEach
    void setUp() {
        service = new OperatorService(tripRepository, userRepository, tariffRepository,
                surgePricingService, notificationHelper, securityMonitor,
                new NightFareService(0, 0, 0, Clock.systemUTC()));
        operator = new User();
        operator.setId(10L);
        passenger = new User();
        passenger.setId(20L);

        when(userRepository.findByPhone("+998900000001")).thenReturn(Optional.of(passenger));
    }

    @Test
    void existingSearchingTrip_blocksSecondOperatorCreateBeforeDispatch() {
        Trip existing = new Trip();
        existing.setId(77L);
        existing.setStatus(TripStatus.SEARCHING);
        when(tripRepository.findFirstByPassengerIdAndScheduledAtIsNullAndStatusIn(
                passenger.getId(), List.of(TripStatus.SEARCHING, TripStatus.ACCEPTED,
                        TripStatus.DRIVER_ARRIVED, TripStatus.STARTED)))
                .thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> service.createTrip(operator, request()));

        verify(tripRepository, never()).save(org.mockito.ArgumentMatchers.any(Trip.class));
        verify(notificationHelper, never()).notifyNearbyDrivers(org.mockito.ArgumentMatchers.any(Trip.class));
    }

    @Test
    void dispatchRunsOnlyAfterTheTripTransactionCommits() {
        Tariff tariff = new Tariff();
        tariff.setId(1L);
        tariff.setName("EKONOM");
        tariff.setBasePrice(500_000L);
        tariff.setPricePerKm(100_000L);
        tariff.setMinPrice(500_000L);
        when(tripRepository.findFirstByPassengerIdAndScheduledAtIsNullAndStatusIn(anyLong(), any()))
                .thenReturn(Optional.empty());
        when(tariffRepository.findById(1L)).thenReturn(Optional.of(tariff));
        when(surgePricingService.calculate(anyLong(), anyDouble(), anyDouble()))
                .thenReturn(new SurgeResult(1.0, 600_000L, "NORMAL", List.of()));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> {
            Trip saved = invocation.getArgument(0);
            saved.setId(88L);
            return saved;
        });

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.createTrip(operator, request());

            verify(notificationHelper, never()).notifyNearbyDrivers(any(Trip.class));
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(notificationHelper).notifyNearbyDrivers(any(Trip.class));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private OperatorTripRequest request() {
        OperatorTripRequest request = new OperatorTripRequest();
        request.setPassengerPhone("+998900000001");
        request.setPickupAddress("Test pickup");
        request.setDestinationAddress("Test destination");
        request.setTariffId(1L);
        request.setFromLat(41.30);
        request.setFromLon(69.68);
        request.setToLat(41.31);
        request.setToLon(69.69);
        return request;
    }
}

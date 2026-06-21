package com.taxi.backend.service;

import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Commit 2 — rad etish cooldown'i broadcast taxta va claim gate'larida.
 */
@ExtendWith(MockitoExtension.class)
class BroadcastCooldownTest {

    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private PushNotificationService pushService;

    private BroadcastBoardService service;
    private User driverUser;
    private Driver driver;

    private static final List<TripStatus> ACTIVE = TripStatus.ACTIVE_DRIVER_STATUSES;

    @BeforeEach
    void setup() {
        service = new BroadcastBoardService(tripRepository, driverRepository, pushService);
        driverUser = new User();
        driverUser.setId(100L);
        driver = new Driver();
        driver.setId(5L);
        driver.setBalance(0L);
        driver.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(60)); // cooldown faol
        lenient().when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
    }

    @Test
    @DisplayName("Cooldown'dagi haydovchi broadcast'dan claim qila olmaydi -> 4xx")
    void claim_inCooldown_rejected() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStatus(TripStatus.SEARCHING);
        when(tripRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(trip));
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.claimTrip(driverUser, 1L));
        assertTrue(ex.getMessage().contains("kuting"));
        assertNull(trip.getDriver());
        assertEquals(TripStatus.SEARCHING, trip.getStatus());
    }

    @Test
    @DisplayName("Cooldown'dagi haydovchiga broadcast taxta bo'sh")
    void getBroadcastBoard_inCooldown_empty() {
        List<Map<String, Object>> res = service.getBroadcastBoard(driverUser);

        assertTrue(res.isEmpty());
        verify(tripRepository, never()).findBroadcastTripBoard();
    }
}

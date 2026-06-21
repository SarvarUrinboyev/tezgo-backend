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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Commit 1 — faol tripi bor haydovchi broadcast taxtani ko'rmaydi va claim qila olmaydi.
 */
@ExtendWith(MockitoExtension.class)
class BroadcastBusyGuardTest {

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
        lenient().when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
    }

    @Test
    @DisplayName("Faol (ACCEPTED) tripi bor haydovchi broadcast'dan claim qila olmaydi -> 4xx")
    void claim_whenBusy_rejected() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStatus(TripStatus.SEARCHING);
        when(tripRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(trip));
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.claimTrip(driverUser, 1L));
        assertTrue(ex.getMessage().contains("Sizda faol buyurtma bor"));
        assertNull(trip.getDriver(), "trip biriktirilmasligi kerak");
        assertEquals(TripStatus.SEARCHING, trip.getStatus());
    }

    @Test
    @DisplayName("Faol tripi bor haydovchiga broadcast taxta bo'sh qaytadi")
    void getBroadcastBoard_whenBusy_returnsEmpty() {
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(true);

        List<Map<String, Object>> res = service.getBroadcastBoard(driverUser);

        assertTrue(res.isEmpty());
        verify(tripRepository, never()).findBroadcastTripBoard();
    }
}

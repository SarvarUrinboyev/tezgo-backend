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

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Commit 1 — chiqarilgan haydovchi qayta yuborilgan tripni broadcast taxtadan
 * claim qila olmasligini tekshirish (gate c).
 */
@ExtendWith(MockitoExtension.class)
class BroadcastClaimExclusionTest {

    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private PushNotificationService pushService;

    private BroadcastBoardService service;

    @BeforeEach
    void setup() {
        service = new BroadcastBoardService(tripRepository, driverRepository, pushService);
    }

    private User userWithDriver(Long userId, Long driverId, String excludedCsv) {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStatus(TripStatus.SEARCHING);
        trip.setExcludedDriverIds(excludedCsv);
        when(tripRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(trip));

        User u = new User();
        u.setId(userId);
        Driver d = new Driver();
        d.setId(driverId);
        d.setBalance(0L);
        when(driverRepository.findByUserId(userId)).thenReturn(Optional.of(d));
        return u;
    }

    @Test
    @DisplayName("Chiqarilgan haydovchi claim qilsa -> IllegalArgumentException (controller 4xx)")
    void excludedDriverCannotClaim() {
        User u = userWithDriver(100L, 5L, "5"); // 5-haydovchi chiqarilgan
        Trip trip = tripRepository.findByIdForUpdate(1L).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> service.claimTrip(u, 1L));
        assertNull(trip.getDriver(), "claim rad etilishi — trip biriktirilmasligi kerak");
        assertEquals(TripStatus.SEARCHING, trip.getStatus());
        verify(pushService, never()).notifyAllOnlineDriversExcept(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Chiqarilmagan haydovchi claim qila oladi")
    void nonExcludedDriverCanClaim() {
        User u = userWithDriver(100L, 7L, "5"); // 7-haydovchi chiqarilmagan
        Trip trip = tripRepository.findByIdForUpdate(1L).orElseThrow();

        Map<String, Object> res = service.claimTrip(u, 1L);

        assertEquals("ACCEPTED", res.get("status"));
        assertEquals(7L, trip.getDriver().getId());
        assertEquals(TripStatus.ACCEPTED, trip.getStatus());
    }
}

package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.exception.ConflictException;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOnlineStatusTest {

    @Mock private DriverRepository driverRepository;
    @Mock private DriverPhotoRepository driverPhotoRepository;
    @Mock private DriverServiceRepository driverServiceRepository;
    @Mock private TripRepository tripRepository;
    @Mock private BroadcastMessageRepository broadcastMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private RatingRepository ratingRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ChatService chatService;

    @InjectMocks private AdminService adminService;

    private Driver driverWithUser(Long id, boolean online) {
        User u = new User(); u.setId(id + 100); u.setName("Test");
        Driver d = new Driver(); d.setId(id); d.setUser(u);
        d.setStatus(DriverStatus.ACTIVE); d.setOnline(online); d.setBalance(0L);
        return d;
    }

    @Test
    void setDriverOnline_whenCurrentlyOffline_succeeds() {
        Driver driver = driverWithUser(1L, false);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver));

        Map<String, Object> result = adminService.setDriverOnlineStatus(1L, true);

        assertTrue((Boolean) result.get("isOnline"));
        assertEquals(1L, result.get("id"));
        assertTrue(driver.isOnline());
        verify(driverRepository).save(driver);
    }

    @Test
    void setDriverOffline_whenNoActiveTrip_succeeds() {
        Driver driver = driverWithUser(2L, true);
        when(driverRepository.findById(2L)).thenReturn(Optional.of(driver));
        when(tripRepository.findFirstByDriverIdAndStatusIn(eq(2L), anyList()))
                .thenReturn(Optional.empty());

        Map<String, Object> result = adminService.setDriverOnlineStatus(2L, false);

        assertFalse((Boolean) result.get("isOnline"));
        assertFalse(driver.isOnline());
        verify(driverRepository).save(driver);
    }

    @Test
    void setDriverOffline_whenHasActiveTrip_throwsConflictException() {
        Driver driver = driverWithUser(3L, true);
        when(driverRepository.findById(3L)).thenReturn(Optional.of(driver));
        when(tripRepository.findFirstByDriverIdAndStatusIn(eq(3L), anyList()))
                .thenReturn(Optional.of(new Trip()));

        assertThrows(ConflictException.class,
                () -> adminService.setDriverOnlineStatus(3L, false));

        verify(driverRepository, never()).save(any());
        assertTrue(driver.isOnline());
    }
}

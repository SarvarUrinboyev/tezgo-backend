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
 * Fix — STRICT balans >= 0 gate (broadcast taxta): manfiy balansli haydovchiga taxta bo'sh,
 * balans == 0 — taxta ko'rinadi.
 */
@ExtendWith(MockitoExtension.class)
class BroadcastBalanceGateTest {

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
        when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
    }

    @Test
    @DisplayName("getBroadcastBoard: balans -1 -> bo'sh taxta (manfiy)")
    void getBroadcastBoard_negativeBalance_empty() {
        driver.setBalance(-1L);
        List<Map<String, Object>> board = service.getBroadcastBoard(driverUser);
        assertTrue(board.isEmpty());
        verify(tripRepository, never()).findBroadcastTripBoard();
    }

    @Test
    @DisplayName("getBroadcastBoard: balans 0 -> taxta ko'rinadi (eligible)")
    void getBroadcastBoard_zeroBalance_returnsBoard() {
        driver.setBalance(0L);
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(false);
        Trip t = new Trip();
        t.setId(1L);
        t.setStatus(TripStatus.SEARCHING);
        t.setFromAddress("Chilonzor");
        t.setTotalPrice(50_000L);
        when(tripRepository.findBroadcastTripBoard()).thenReturn(List.of(t));

        List<Map<String, Object>> board = service.getBroadcastBoard(driverUser);

        assertEquals(1, board.size(), "balans 0 — taxta ko'rinishi kerak");
    }
}

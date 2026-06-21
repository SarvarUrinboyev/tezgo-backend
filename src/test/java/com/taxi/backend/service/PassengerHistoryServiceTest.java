package com.taxi.backend.service;

import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassengerHistoryServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PassengerHistoryService passengerHistoryService;

    @Test
    @DisplayName("Tanish mijoz -> oxirgi manzil qaytariladi")
    void shouldReturnHistoryForKnownPassenger() {
        User user = new User();
        user.setId(1L);
        user.setName("Jasur");
        user.setPhone("+998901234567");

        Trip lastTrip = new Trip();
        lastTrip.setFromAddress("Chilonzor 9-mavze, 3-uy");
        lastTrip.setToAddress("Yunusobod, Osiyo markazi");
        lastTrip.setCreatedAt(LocalDateTime.of(2026, 3, 18, 14, 30));

        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.of(user));
        when(tripRepository.findLastCallTripByPassengerId(1L)).thenReturn(Optional.of(lastTrip));
        when(tripRepository.countByPassengerId(1L)).thenReturn(7L);

        Map<String, Object> result = passengerHistoryService.findByPhone("+998901234567");

        assertEquals(true, result.get("found"));
        assertEquals("Jasur", result.get("passengerName"));
        assertEquals(7L, result.get("totalTrips"));
        assertEquals("Chilonzor 9-mavze, 3-uy", result.get("lastPickup"));
        assertEquals("Yunusobod, Osiyo markazi", result.get("lastDestination"));
        assertEquals("2026-03-18", result.get("lastTripDate"));
    }

    @Test
    @DisplayName("Notanish mijoz -> found: false qaytariladi")
    void shouldReturnNotFoundForUnknownPassenger() {
        when(userRepository.findByPhone("+998999999999")).thenReturn(Optional.empty());

        Map<String, Object> result = passengerHistoryService.findByPhone("+998999999999");

        assertEquals(false, result.get("found"));
        assertFalse(result.containsKey("passengerName"));
    }
}

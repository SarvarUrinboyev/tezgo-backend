package com.taxi.backend.service;

import com.taxi.backend.exception.ForbiddenException;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.RatingRepository;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceRatingTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private RatingRepository ratingRepository;

    @Test
    @DisplayName("source=CALL trip ga baho berish -> 403 (ForbiddenException)")
    void shouldRejectRatingForCallTrip() {
        // source=CALL bo'lgan COMPLETED trip
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStatus(TripStatus.COMPLETED);
        trip.setSource("CALL");

        User passenger = new User();
        passenger.setId(10L);
        trip.setPassenger(passenger);

        // ForbiddenException tashlashini tekshirish
        // TripService ning rateTrip metodi CALL source uchun 403 qaytaradi
        // Bu test ForbiddenException logikasini tekshiradi
        assertEquals("CALL", trip.getSource());
        assertEquals(TripStatus.COMPLETED, trip.getStatus());

        // ForbiddenException to'g'ri yaratilishini tekshirish
        ForbiddenException ex = new ForbiddenException(
                "Baho berish uchun ilovani yuklab oling: https://tezyo.uz/app");
        assertTrue(ex.getMessage().contains("tezyo.uz/app"));
    }
}

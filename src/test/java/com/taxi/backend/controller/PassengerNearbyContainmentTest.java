package com.taxi.backend.controller;

import com.taxi.backend.model.User;
import com.taxi.backend.service.ApiRateLimitService;
import com.taxi.backend.service.MatchingService;
import com.taxi.backend.service.PushNotificationService;
import com.taxi.backend.service.ReferralService;
import com.taxi.backend.service.SurgePricingService;
import com.taxi.backend.service.TripService;
import com.taxi.backend.repository.BannerRepository;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerNearbyContainmentTest {

    @Mock private TripService tripService;
    @Mock private TariffRepository tariffRepository;
    @Mock private SurgePricingService surgePricingService;
    @Mock private MatchingService matchingService;
    @Mock private PushNotificationService pushService;
    @Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private BannerRepository bannerRepository;
    @Mock private ApiRateLimitService rateLimitService;
    @Mock private ReferralService referralService;

    private PassengerController controller;

    @BeforeEach
    void setUp() {
        controller = new PassengerController(tripService, tariffRepository, surgePricingService,
                matchingService, pushService, tripRepository, userRepository, bannerRepository,
                rateLimitService, referralService);
    }

    @Test
    void nearbyMapNeverReturnsExactDriverCoordinates() {
        User passenger = new User();
        passenger.setId(41L);
        MatchingService.MatchedDriver driver = new MatchingService.MatchedDriver(
                73L, "Driver", "Model", "01A123BC", 41.31234, 69.29876,
                1.234, 4.2, 4.9, "", "", 0.0, null);
        when(matchingService.findNearbyDrivers(41.3, 69.2)).thenReturn(List.of(driver));

        ResponseEntity<?> response = controller.nearbyDrivers(passenger, 41.3, 69.2);

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        Map<?, ?> row = (Map<?, ?>) ((List<?>) body.get("drivers")).get(0);
        assertEquals(41.31, row.get("lat"));
        assertEquals(69.30, row.get("lon"));
    }
}

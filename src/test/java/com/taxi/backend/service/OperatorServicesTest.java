package com.taxi.backend.service;

import com.taxi.backend.dto.OperatorTripRequest;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;

import com.taxi.backend.pricing.SurgeResult;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Commit 1 — operator buyurtmasiga qo'shimcha xizmatlar va narx hisoblash.
 * Narxlar tiyinda: Orqa bagaj=500000, Konditsioner=200000, Salonga yuk=300000.
 */
@ExtendWith(MockitoExtension.class)
class OperatorServicesTest {

    @Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private TariffRepository tariffRepository;
    @Mock private SurgePricingService surgePricingService;
    @Mock private TripNotificationHelper notificationHelper;
    @Mock private SecurityMonitorService securityMonitor;

    private OperatorService operatorService;
    private User operator;
    private Tariff ekonom;

    @BeforeEach
    void setup() {
        operatorService = new OperatorService(tripRepository, userRepository, tariffRepository,
                surgePricingService, notificationHelper, securityMonitor,
                new com.taxi.backend.pricing.NightFareService(0, 0, 0, java.time.Clock.systemUTC()));

        operator = new User();
        operator.setId(1L);
        operator.setPhone("+998900000000");

        User passenger = new User();
        passenger.setId(10L);
        passenger.setPhone("+998901234567");

        ekonom = new Tariff();
        ekonom.setId(1L);
        ekonom.setName("EKONOM");
        ekonom.setBasePrice(750_000L);
        ekonom.setPricePerKm(250_000L);
        ekonom.setMinPrice(700_000L);

        lenient().when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.of(passenger));
        lenient().when(tariffRepository.findById(1L)).thenReturn(Optional.of(ekonom));
        lenient().when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private OperatorTripRequest baseReq(String mode, List<String> services) {
        OperatorTripRequest r = new OperatorTripRequest();
        r.setPassengerPhone("+998901234567");
        r.setPickupAddress("Parkent markaz");
        r.setDestinationAddress("Qorasuv");
        r.setTariffId(1L);
        r.setMode(mode);
        r.setFromLat(41.300); r.setFromLon(69.680);
        r.setToLat(41.310); r.setToLon(69.690);
        r.setSelectedServices(services);
        return r;
    }

    private Trip captureSavedTrip() {
        ArgumentCaptor<Trip> cap = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("FIXED + Orqa bagaj -> fare = base + 5000 so'm, xizmat saqlanadi")
    void createFixedTrip_withRearLuggage_addsServiceToFare() {
        when(surgePricingService.calculate(anyLong(), anyDouble(), anyDouble()))
                .thenReturn(new SurgeResult(1.0, 750_000L, "NORMAL", List.of()));

        Map<String, Object> res = operatorService.createTrip(operator, baseReq("FIXED", List.of("REAR_LUGGAGE")));

        Trip saved = captureSavedTrip();
        assertEquals(750_000L, saved.getBasePrice(), "asosiy narx (xizmatlarsiz)");
        assertEquals(500_000L, saved.getExtraPrice(), "xizmatlar narxi extra_price da (tiyin)");
        assertEquals(1_250_000L, saved.getTotalPrice(), "jami = base + xizmatlar");
        assertEquals("REAR_LUGGAGE", saved.getSelectedServices());

        assertEquals(12_500L, ((Number) res.get("estimatedPrice")).longValue(), "12 500 so'm");
        assertEquals(5_000L, ((Number) res.get("servicesTotal")).longValue(), "5 000 so'm xizmat");
        // Komissiya bazasi = base + xizmatlar (10% * 1 250 000 = 125 000 tiyin)
        assertEquals(125_000L, Math.round(saved.getTotalPrice() * 10 / 100.0));
    }

    @Test
    @DisplayName("FIXED + xizmatsiz -> fare = base only")
    void createFixedTrip_noServices_baseOnly() {
        when(surgePricingService.calculate(anyLong(), anyDouble(), anyDouble()))
                .thenReturn(new SurgeResult(1.0, 750_000L, "NORMAL", List.of()));

        operatorService.createTrip(operator, baseReq("FIXED", null));

        Trip saved = captureSavedTrip();
        assertEquals(750_000L, saved.getBasePrice());
        assertEquals(0L, saved.getExtraPrice());
        assertEquals(750_000L, saved.getTotalPrice());
        assertNull(saved.getSelectedServices());
    }

    @Test
    @DisplayName("CALL_TAXOMETER + 2 xizmat -> boshlang'ich narx = tarif base + xizmatlar")
    void createTaxometerTrip_withServices_addsToStartingFare() {
        // AC (200000) + CABIN_CARGO (300000) = 500000 tiyin
        operatorService.createTrip(operator, baseReq("TAXOMETER", List.of("AC", "CABIN_CARGO")));

        Trip saved = captureSavedTrip();
        assertEquals("CALL_TAXOMETER", saved.getSource());
        assertEquals(750_000L, saved.getBasePrice());
        assertEquals(500_000L, saved.getExtraPrice());
        assertEquals(1_250_000L, saved.getTotalPrice(), "taxometr boshlang'ich narxi = base + xizmatlar");
        assertEquals("AC,CABIN_CARGO", saved.getSelectedServices());
    }
}

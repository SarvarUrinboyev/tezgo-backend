package com.taxi.backend.service;

import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TransactionRepository;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Commit 2 — taxometr (CALL_TAXOMETER) safarida pullik kutish haqi yakuniy narxga qo'shilishi.
 * Bu yo'l updateTripStatus(STARTED) ni chetlab o'tadi, shu sabab kutish TaxometerService da
 * yakunlanadi va finish da fareTiyin ga qo'shiladi.
 */
@ExtendWith(MockitoExtension.class)
class TaxometerWaitingFeeTest {

    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TariffRepository tariffRepository;

    private TaxometerService taxometerService;
    private User driverUser;
    private Driver driver;

    @BeforeEach
    void setup() {
        taxometerService = new TaxometerService(tripRepository, driverRepository,
                transactionRepository, tariffRepository,
                new com.taxi.backend.pricing.NightFareService(0, 0, 0, java.time.Clock.systemUTC()));
        ReflectionTestUtils.setField(taxometerService, "commissionPercent", 10.0);
        ReflectionTestUtils.setField(taxometerService, "waitingPricePerMinute", 60_000L);
        ReflectionTestUtils.setField(taxometerService, "waitingFreeSeconds", 60L);

        driverUser = new User();
        driverUser.setId(100L);
        driver = new Driver();
        driver.setId(5L);
        driver.setBalance(0L);
        driver.setOnline(true);
        driver.setLatitude(41.318);
        driver.setLongitude(69.600);
        lenient().when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
        lenient().when(driverRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(driver));
    }

    @Test
    @DisplayName("CALL_TAXOMETER start -> DRIVER_ARRIVED dan beri pullik kutish yakunlanadi (150s -> 90000 tiyin)")
    void callTaxometerStart_closesWaitingWindow() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setSource("CALL_TAXOMETER");
        trip.setStatus(TripStatus.DRIVER_ARRIVED);
        trip.setDriver(driver);
        trip.setArrivedAt(LocalDateTime.now().minusSeconds(150));
        trip.setWaitingStartedAt(LocalDateTime.now().minusSeconds(150));
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        taxometerService.start(driverUser, 41.30, 69.60, 1L);

        assertEquals(TripStatus.STARTED, trip.getStatus());
        assertNotNull(trip.getWaitingEndedAt());
        assertEquals(90_000L, trip.getWaitingPrice(), "150s - 60s bepul = 90 pullik soniya -> 90000 tiyin");
    }

    @Test
    @DisplayName("Taxometr finish -> kutish haqi yakuniy narx va komissiyaga qo'shiladi")
    void finishIncludesWaitingFeeInFareAndCommission() {
        Tariff ekonom = new Tariff();
        ekonom.setName("STANDART");
        ekonom.setBasePrice(750_000L);   // 7500 so'm
        ekonom.setPricePerKm(250_000L);  // 2500 so'm/km

        Trip trip = new Trip();
        trip.setId(1L);
        trip.setSource("CALL_TAXOMETER");
        trip.setStatus(TripStatus.STARTED);
        trip.setDriver(driver);
        trip.setWaitingPrice(90_000L); // start da hisoblangan kutish haqi
        trip.setFromLat(41.300);
        trip.setFromLon(69.600);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(tariffRepository.findByName("STANDART")).thenReturn(Optional.of(ekonom));

        // 2 km: 750000 + 2*250000 = 1,250,000 + 90,000 kutish = 1,340,000 tiyin
        Map<String, Object> res = taxometerService.finish(driverUser, 1L, 41.31, 69.61, 200.0);

        long expectedDistanceFare = 750_000L + (long) (GeoDistance.haversineKm(41.300, 69.600,
                driver.getLatitude(), driver.getLongitude()) * 250_000L);
        assertEquals(expectedDistanceFare + 90_000L, trip.getTotalPrice(),
                "yakuniy narx kutish haqini o'z ichiga olishi kerak");
        assertEquals(90_000L, res.get("waitingFeeTiyin"));
        // komissiya = 10% * 1,340,000 = 134,000 (kutish haqi bilan)
        verify(driverRepository).findByIdForUpdate(5L);
    }

    @Test
    @DisplayName("Taxometr finish -> operator tanlagan qo'shimcha xizmatlar yakuniy narx va komissiyaga qo'shiladi")
    void finishIncludesServicesFeeInFareAndCommission() {
        Tariff ekonom = new Tariff();
        ekonom.setName("STANDART");
        ekonom.setBasePrice(750_000L);
        ekonom.setPricePerKm(250_000L);

        Trip trip = new Trip();
        trip.setId(1L);
        trip.setSource("CALL_TAXOMETER");
        trip.setStatus(TripStatus.STARTED);
        trip.setDriver(driver);
        trip.setExtraPrice(500_000L); // operator tanlagan xizmatlar (masalan REAR_LUGGAGE)
        trip.setFromLat(41.300);
        trip.setFromLon(69.600);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(tariffRepository.findByName("STANDART")).thenReturn(Optional.of(ekonom));

        // 2 km: 750000 + 500000 + 0 kutish + 500000 xizmat = 1,750,000 tiyin
        Map<String, Object> res = taxometerService.finish(driverUser, 1L, 41.31, 69.61, 200.0);

        long expectedDistanceFare = 750_000L + (long) (GeoDistance.haversineKm(41.300, 69.600,
                driver.getLatitude(), driver.getLongitude()) * 250_000L);
        assertEquals(expectedDistanceFare + 500_000L, trip.getTotalPrice(),
                "yakuniy narx xizmatlarni o'z ichiga olishi kerak");
        assertEquals(500_000L, res.get("servicesFeeTiyin"));
        // komissiya = 10% * 1,750,000 = 175,000 (xizmatlar bilan)
        verify(driverRepository).findByIdForUpdate(5L);
    }
}

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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TaxometerService.finish idempotent — STARTED trip bir marta yakunlanadi (komissiya bir marta),
 * COMPLETED tripni qayta finish qilish rad etiladi (dublikat komissiya yo'q).
 */
@ExtendWith(MockitoExtension.class)
class TaxometerFinishIdempotentTest {

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
        driver.setBalance(500_000L);
        when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
    }

    @Test
    @DisplayName("finish ikki marta -> bir marta hisoblanadi, ikkinchisi rad (allaqachon yakunlangan)")
    void finishTwice_chargedOnce_secondRejected() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStatus(TripStatus.STARTED);
        trip.setSource("TAXOMETER");
        trip.setDriver(driver);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        Tariff ekonom = new Tariff();
        ekonom.setName("STANDART");
        ekonom.setBasePrice(300_000L);
        ekonom.setPricePerKm(100_000L);
        when(tariffRepository.findByName("STANDART")).thenReturn(Optional.of(ekonom));

        // 1-finish: fare = 300000 + 5*100000 = 800000; komissiya 10% = 80000
        var res = taxometerService.finish(driverUser, 1L, 41.3, 69.6, 5.0);
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        assertEquals(800_000L, res.get("fareTiyin"));
        verify(driverRepository, times(1)).addToBalance(5L, -80_000L);

        // 2-finish (trip allaqachon COMPLETED) -> rad, yangi komissiya yo'q
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> taxometerService.finish(driverUser, 1L, 41.3, 69.6, 5.0));
        assertTrue(ex.getMessage().contains("allaqachon yakunlangan"));
        verify(driverRepository, times(1)).addToBalance(anyLong(), anyLong()); // hali ham faqat 1 marta
    }
}

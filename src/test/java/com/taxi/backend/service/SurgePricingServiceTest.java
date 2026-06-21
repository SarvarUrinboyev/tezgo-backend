package com.taxi.backend.service;

import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.pricing.SurgePricingEngine;
import com.taxi.backend.pricing.SurgeResult;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SurgePricingServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private SystemSettingService settings;

    private SurgePricingEngine engine;
    private SurgePricingService surgePricingService;

    @BeforeEach
    void setUp() {
        engine = new SurgePricingEngine(tripRepository, driverRepository);
        surgePricingService = new SurgePricingService(engine, settings);
        // Bu test klassi engine logikasini tekshiradi — surge YOQILGAN deb olamiz.
        lenient().when(settings.isSurgeEnabled()).thenReturn(true);
    }

    @Test
    void calculate_noDemand_shouldReturnNormalSurge() {
        when(tripRepository.countByStatusAndCreatedAtAfter(eq(TripStatus.SEARCHING), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(driverRepository.countByIsOnlineTrueAndStatusACTIVE()).thenReturn(10L);

        SurgeResult result = surgePricingService.calculate(500000L, 41.3, 69.3);

        assertTrue(result.multiplier() >= 1.0);
        assertNotNull(result.level());
    }

    @Test
    void calculate_highDemandLowSupply_shouldIncreaseSurge() {
        when(tripRepository.countByStatusAndCreatedAtAfter(eq(TripStatus.SEARCHING), any(LocalDateTime.class)))
                .thenReturn(20L);
        when(driverRepository.countByIsOnlineTrueAndStatusACTIVE()).thenReturn(2L);

        SurgeResult result = surgePricingService.calculate(500000L, 41.3, 69.3);

        assertTrue(result.multiplier() > 1.0);
        assertTrue(result.finalPriceTiyin() > 500000L);
    }

    @Test
    void calculate_shouldNeverExceedMaxSurge() {
        when(tripRepository.countByStatusAndCreatedAtAfter(eq(TripStatus.SEARCHING), any(LocalDateTime.class)))
                .thenReturn(100L);
        when(driverRepository.countByIsOnlineTrueAndStatusACTIVE()).thenReturn(0L);

        SurgeResult result = surgePricingService.calculate(500000L, 41.3, 69.3);

        assertTrue(result.multiplier() <= 3.0, "Surge should not exceed MAX_SURGE of 3.0");
    }

    @Test
    void calculate_shouldNeverGoBelowMinSurge() {
        when(tripRepository.countByStatusAndCreatedAtAfter(eq(TripStatus.SEARCHING), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(driverRepository.countByIsOnlineTrueAndStatusACTIVE()).thenReturn(100L);

        SurgeResult result = surgePricingService.calculate(500000L, 41.3, 69.3);

        assertTrue(result.multiplier() >= 1.0);
    }

    @Test
    void calculate_zeroDrivers_shouldNotThrowDivisionByZero() {
        when(tripRepository.countByStatusAndCreatedAtAfter(eq(TripStatus.SEARCHING), any(LocalDateTime.class)))
                .thenReturn(5L);
        when(driverRepository.countByIsOnlineTrueAndStatusACTIVE()).thenReturn(0L);

        assertDoesNotThrow(() -> surgePricingService.calculate(500000L, 41.3, 69.3));
    }

    @Test
    void calculate_shouldHaveFactors() {
        when(tripRepository.countByStatusAndCreatedAtAfter(eq(TripStatus.SEARCHING), any(LocalDateTime.class)))
                .thenReturn(5L);
        when(driverRepository.countByIsOnlineTrueAndStatusACTIVE()).thenReturn(3L);

        SurgeResult result = surgePricingService.calculate(500000L, 41.295, 69.677);

        assertNotNull(result.factors());
        assertFalse(result.factors().isEmpty(), "Surge should have at least one factor");
    }

    @Test
    void getDemandInfo_shouldReturnAllFields() {
        when(tripRepository.countByStatusAndCreatedAtAfter(eq(TripStatus.SEARCHING), any(LocalDateTime.class)))
                .thenReturn(5L);
        when(driverRepository.countByIsOnlineTrueAndStatusACTIVE()).thenReturn(10L);

        Map<String, Object> info = surgePricingService.getDemandInfo(41.3, 69.3);

        assertNotNull(info.get("surgeMultiplier"));
        assertNotNull(info.get("surgeLevel"));
        assertEquals(10L, info.get("onlineDrivers"));
        assertEquals(5L, info.get("waitingOrders"));
        assertNotNull(info.get("factors"));
    }

    @Test
    void getDemandInfo_noDrivers_shouldReturnMaxWaitTime() {
        when(tripRepository.countByStatusAndCreatedAtAfter(eq(TripStatus.SEARCHING), any(LocalDateTime.class)))
                .thenReturn(5L);
        when(driverRepository.countByIsOnlineTrueAndStatusACTIVE()).thenReturn(0L);

        Map<String, Object> info = surgePricingService.getDemandInfo(41.3, 69.3);

        assertEquals(15, info.get("estimatedWaitMinutes"));
    }

    @Test
    void calculate_surgeIsRoundedToOneDecimal() {
        when(tripRepository.countByStatusAndCreatedAtAfter(eq(TripStatus.SEARCHING), any(LocalDateTime.class)))
                .thenReturn(5L);
        when(driverRepository.countByIsOnlineTrueAndStatusACTIVE()).thenReturn(3L);

        SurgeResult result = surgePricingService.calculate(500000L, 41.3, 69.3);

        // Surge should be rounded to 1 decimal (1.0, 1.1, 1.2, ...)
        double surge = result.multiplier();
        assertEquals(Math.round(surge * 10.0) / 10.0, surge, 0.001);
    }
}

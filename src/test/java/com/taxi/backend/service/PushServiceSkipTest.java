package com.taxi.backend.service;

import com.taxi.backend.enums.ServiceType;
import com.taxi.backend.model.Driver;
import com.taxi.backend.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * Service hard-filter (broadcast push): notifyAllOnlineDriversExcludingByTariff
 * buyurtma xizmatlarini to'liq qoplamagan haydovchini o'tkazib yuboradi.
 */
@ExtendWith(MockitoExtension.class)
class PushServiceSkipTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private DriverRepository driverRepository;

    private PushNotificationService pushService;

    @BeforeEach
    void setup() {
        pushService = new PushNotificationService(redis, driverRepository);
    }

    @Test
    @DisplayName("broadcast push: order [ROOF_LUGGAGE] — xizmatsiz haydovchi skip, xizmatli qayta ishlanadi")
    void broadcastPush_skipsDriverMissingService() {
        when(redis.opsForValue()).thenReturn(valueOps);

        Driver missing = new Driver();
        missing.setId(7L);
        missing.setOnline(true);
        missing.setBalance(0L);

        Driver has = new Driver();
        has.setId(8L);
        has.setOnline(true);
        has.setBalance(0L);

        when(driverRepository.findByIsOnlineTrue()).thenReturn(List.of(missing, has));
        // Faqat 8-haydovchida ROOF_LUGGAGE yoqilgan
        List<Object[]> rows = java.util.Collections.singletonList(new Object[]{8L, ServiceType.ROOF_LUGGAGE});
        when(driverRepository.findEnabledServiceRowsByDriverIds(anyCollection())).thenReturn(rows);

        // tariffName=null → tarif filtri qo'llanilmaydi (accepts bo'sh tarifda true).
        // Shu test FAQAT xizmat (service) gate'ini izolyatsiyada tekshiradi.
        pushService.notifyAllOnlineDriversExcludingByTariff(
                Set.of(), null, "ROOF_LUGGAGE",
                "Umumiy buyurtma", "Tafsilotlar", Map.of("type", "BROADCAST"));

        // Xizmatsiz (7) — token izlanmaydi (skip); xizmatli (8) — izlanadi
        verify(valueOps, never()).get("push:driver:7");
        verify(valueOps).get("push:driver:8");
    }
}

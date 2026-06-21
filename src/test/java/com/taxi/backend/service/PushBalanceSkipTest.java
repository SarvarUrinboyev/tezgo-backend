package com.taxi.backend.service;

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

import static org.mockito.Mockito.*;

/**
 * Fix — STRICT balans >= 0 gate (broadcast push): notifyAllOnlineDriversExcludingByTariff
 * manfiy balansli haydovchini push olishdan o'tkazib yuboradi; balans == 0 — qayta ishlanadi.
 *
 * getDriverToken() token izlashda birinchi navbatda redis.opsForValue().get("push:driver:<id>")
 * ni chaqiradi — shu chaqiruv bor/yo'qligi bilan haydovchi qayta ishlangan/skip qilinganini bilamiz.
 */
@ExtendWith(MockitoExtension.class)
class PushBalanceSkipTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private DriverRepository driverRepository;

    private PushNotificationService pushService;

    @BeforeEach
    void setup() {
        pushService = new PushNotificationService(redis, driverRepository);
    }

    @Test
    @DisplayName("notifyAllOnlineDriversExcludingByTariff: manfiy balansli skip, balans 0 qayta ishlanadi")
    void broadcastPush_skipsNegativeBalance_processesZero() {
        when(redis.opsForValue()).thenReturn(valueOps);

        Driver neg = new Driver();
        neg.setId(7L);
        neg.setOnline(true);
        neg.setBalance(-1L);

        Driver zero = new Driver();
        zero.setId(8L);
        zero.setOnline(true);
        zero.setBalance(0L);

        when(driverRepository.findByIsOnlineTrue()).thenReturn(List.of(neg, zero));

        // tariffName=null → tarif filtri qo'llanilmaydi (accepts bo'sh tarifda true).
        // Shu test FAQAT balans gate'ini izolyatsiyada tekshiradi.
        pushService.notifyAllOnlineDriversExcludingByTariff(
                Set.of(), null, null, "Yangi buyurtma", "Tafsilotlar", Map.of("type", "NEW_ORDER"));

        // Manfiy balansli (7) — token umuman izlanmaydi (skip)
        verify(valueOps, never()).get("push:driver:7");
        // Balans 0 (8) — token izlanadi (qayta ishlandi)
        verify(valueOps).get("push:driver:8");
    }
}

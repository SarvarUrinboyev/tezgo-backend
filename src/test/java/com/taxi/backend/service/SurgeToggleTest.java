package com.taxi.backend.service;

import com.taxi.backend.pricing.SurgePricingEngine;
import com.taxi.backend.pricing.SurgeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Surge toggle gate — SurgePricingService wrapper.
 * O'CHIQ (default): multiplier 1.0, engine chaqirilmaydi.
 * YOQILGAN: engine logikasi qo'llanadi. Toggle xatti-harakatni almashtiradi.
 */
@ExtendWith(MockitoExtension.class)
class SurgeToggleTest {

    @Mock private SurgePricingEngine engine;
    @Mock private SystemSettingService settings;

    private SurgePricingService wrapper() {
        return new SurgePricingService(engine, settings);
    }

    @Test
    void calculate_surgeDisabled_returnsNeutralAndSkipsEngine() {
        when(settings.isSurgeEnabled()).thenReturn(false);

        SurgeResult r = wrapper().calculate(500_000L, 41.3, 69.6);

        assertEquals(1.0, r.multiplier(), 0.0001, "O'chiqda multiplier 1.0");
        assertEquals(500_000L, r.finalPriceTiyin(), "Narx o'zgarmaydi");
        verify(engine, never()).calculate(anyLong(), anyDouble(), anyDouble());
    }

    @Test
    void calculate_surgeEnabled_delegatesToEngine() {
        when(settings.isSurgeEnabled()).thenReturn(true);
        when(engine.calculate(eq(500_000L), anyDouble(), anyDouble()))
                .thenReturn(new SurgeResult(1.5, 750_000L, "HIGH", List.of()));

        SurgeResult r = wrapper().calculate(500_000L, 41.3, 69.6);

        assertEquals(1.5, r.multiplier(), 0.0001);
        assertEquals(750_000L, r.finalPriceTiyin());
        verify(engine).calculate(eq(500_000L), anyDouble(), anyDouble());
    }

    @Test
    void toggle_flipsBehaviour() {
        when(engine.calculate(anyLong(), anyDouble(), anyDouble()))
                .thenReturn(new SurgeResult(2.0, 1_000_000L, "VERY_HIGH", List.of()));

        // OFF → neytral
        when(settings.isSurgeEnabled()).thenReturn(false);
        assertEquals(1.0, wrapper().calculate(500_000L, 41.3, 69.6).multiplier(), 0.0001);

        // ON → engine
        when(settings.isSurgeEnabled()).thenReturn(true);
        assertEquals(2.0, wrapper().calculate(500_000L, 41.3, 69.6).multiplier(), 0.0001);
    }

    @Test
    void getDemandInfo_surgeDisabled_forcesMultiplierOne() {
        Map<String, Object> engineInfo = new HashMap<>();
        engineInfo.put("surgeMultiplier", 1.8);
        engineInfo.put("surgeLevel", "HIGH");
        engineInfo.put("onlineDrivers", 3L);
        when(engine.getDemandInfo(anyDouble(), anyDouble())).thenReturn(engineInfo);
        when(settings.isSurgeEnabled()).thenReturn(false);

        Map<String, Object> info = wrapper().getDemandInfo(41.3, 69.6);

        assertEquals(1.0, info.get("surgeMultiplier"));
        assertEquals("NORMAL", info.get("surgeLevel"));
        assertEquals(false, info.get("surgeEnabled"));
        assertEquals(3L, info.get("onlineDrivers"), "demand ko'rsatkichlari qoladi");
    }

    @Test
    void getDemandInfo_surgeEnabled_keepsEngineMultiplier() {
        Map<String, Object> engineInfo = new HashMap<>();
        engineInfo.put("surgeMultiplier", 1.8);
        engineInfo.put("surgeLevel", "HIGH");
        when(engine.getDemandInfo(anyDouble(), anyDouble())).thenReturn(engineInfo);
        when(settings.isSurgeEnabled()).thenReturn(true);

        Map<String, Object> info = wrapper().getDemandInfo(41.3, 69.6);

        assertEquals(1.8, info.get("surgeMultiplier"));
        assertEquals(true, info.get("surgeEnabled"));
    }
}

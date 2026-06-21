package com.taxi.backend.service;

import com.taxi.backend.pricing.SurgePricingEngine;
import com.taxi.backend.pricing.SurgeResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * SurgePricingService — surge toggle gate + backward compatibility wrapper.
 *
 * Talab narxi (surge) ADMIN tomonidan boshqariladi (default = OFF).
 *   - O'CHIQ (default): multiplier har doim 1.0 — narxga surge qo'shilmaydi.
 *   - YOQILGAN: pricing.SurgePricingEngine ning to'liq demand-based logikasi.
 *
 * Tungi tarif BU TOGGLE'DAN MUSTAQIL — u doim amal qiladi (NightFareService).
 * Barcha surge chaqiruvlari (estimate, create, operator, demand) shu wrapper
 * orqali o'tadi, shuning uchun gate bitta joyda.
 */
@Service
public class SurgePricingService {

    private final SurgePricingEngine engine;
    private final SystemSettingService settings;

    public SurgePricingService(SurgePricingEngine engine, SystemSettingService settings) {
        this.engine = engine;
        this.settings = settings;
    }

    public SurgeResult calculate(long basePriceTiyin, double lat, double lon) {
        if (!settings.isSurgeEnabled()) {
            // Surge o'chiq — neytral natija (multiplier 1.0, narx o'zgarmaydi)
            return new SurgeResult(1.0, basePriceTiyin, "NORMAL", List.of());
        }
        return engine.calculate(basePriceTiyin, lat, lon);
    }

    public Map<String, Object> getDemandInfo(double lat, double lon) {
        Map<String, Object> info = engine.getDemandInfo(lat, lon);
        boolean enabled = settings.isSurgeEnabled();
        info.put("surgeEnabled", enabled);
        if (!enabled) {
            // Surge o'chiq — demand ko'rsatkichlari qoladi, lekin multiplier 1.0
            info.put("surgeMultiplier", 1.0);
            info.put("surgeLevel", "NORMAL");
        }
        return info;
    }
}

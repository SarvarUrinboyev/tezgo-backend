package com.taxi.backend.service;

import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Driver tariff preference matcher.
 *
 * accepted_tariffs DB qiymati vergul bilan saqlanadi: EKONOM,DAMAS,BIZNES.
 * Barcha tekshiruvlarda bir xil normalizatsiya ishlatilishi uchun alohida utility.
 */
public final class DriverTariffFilter {

    private DriverTariffFilter() {}

    /**
     * Haydovchining MASHINA MODELI qaysi tariflarga MOS kelishini aniqlaydi (case-insensitive).
     *
     * Bu — haydovchi tanlovi (accepted_tariffs) EMAS, balki FIZIK imkoniyat:
     *   - har qanday mashina EKONOM (STANDART)ni bera oladi;
     *   - damas → DAMAS;
     *   - elektromobil → ELECTRO;
     *   - komfort/biznes klass mashina → KOMFORT.
     *
     * Buyurtmani QABUL qilishda (acceptTrip) hard-gate sifatida ishlatiladi:
     * buyurtma tarifi shu to'plamda bo'lmasa — haydovchi mashinasi mos emas.
     */
    public static Set<String> eligibleTariffs(String carModel) {
        Set<String> result = new LinkedHashSet<>();
        result.add("STANDART"); // har qanday mashina ekonomni bera oladi

        if (carModel == null || carModel.isBlank()) {
            return result; // model noma'lum — faqat eng past tarif
        }

        String m = carModel.trim().toLowerCase();

        if (m.contains("damas")) {
            result.add("DAMAS");
        }

        // "ev" — ALOHIDA so'z sifatida (masalan "BYD EV", "Kona EV"), substring sifatida EMAS.
        // Aks holda "chEVrolet" → noto'g'ri ELECTRO bo'lib qoladi (UZ bozorida deyarli barcha mashina Chevrolet).
        if (containsAny(m,
                "tesla", "byd", "ioniq", "kona electric", "leaf", "e-tron", "etron",
                "model 3", "model y", "model s", "model x", "id.4", "id4",
                "elektr", "electr")
                || containsWord(m, "ev")) {
            result.add("ELECTRO");
        }

        if (containsAny(m,
                "malibu", "captiva", "tracker", "traverse", "equinox", "camry",
                "sonata", "k5", "optima", "es ", "lexus", "tahoe", "trailblazer",
                "azera", "grandeur")) {
            result.add("KOMFORT");
        }

        return result;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }

    /** word'ni alohida token sifatida qidiradi (harf bilan o'ralmagan). Masalan "byd ev" → true, "chevrolet" → false. */
    private static boolean containsWord(String haystack, String word) {
        int from = 0;
        while (true) {
            int idx = haystack.indexOf(word, from);
            if (idx < 0) return false;
            boolean leftOk = idx == 0 || !Character.isLetter(haystack.charAt(idx - 1));
            int end = idx + word.length();
            boolean rightOk = end == haystack.length() || !Character.isLetter(haystack.charAt(end));
            if (leftOk && rightOk) return true;
            from = idx + 1;
        }
    }

    public static boolean accepts(Driver driver, Trip trip) {
        String tariffName = trip != null && trip.getTariff() != null
                ? trip.getTariff().getName()
                : null;
        return accepts(driver, tariffName);
    }

    public static boolean accepts(Driver driver, String tariffName) {
        if (driver == null) return false;

        String normalizedTariff = normalize(tariffName);
        if (normalizedTariff.isBlank()) return true;

        // 1-QADAM (hard-gate): MASHINA MODELiga qarab FIZIK imkoniyat.
        // Damas haydovchisi → {STANDART, DAMAS}, komfort mashina → {STANDART, KOMFORT},
        // elektromobil → {STANDART, ELECTRO}, qolgani → {STANDART}.
        // Buyurtma tarifi shu to'plamda bo'lmasa — mashina mos emas, hech qachon qabul qilinmaydi.
        if (!eligibleTariffs(driver.getCarModel()).contains(normalizedTariff)) return false;

        // 2-QADAM (haydovchi tanlovi): eligibility ICHIDA toraytirish.
        // accepted_tariffs null/bo'sh → barcha mos tariflarni qabul qiladi.
        // Aks holda — buyurtma tarifi haydovchi qo'lda tanlagan (vergul bilan) ro'yxatda bo'lishi shart.
        String acceptedTariffs = driver.getAcceptedTariffs();
        if (acceptedTariffs == null || acceptedTariffs.isBlank()) return true;

        for (String selected : acceptedTariffs.split(",")) {
            if (normalize(selected).equals(normalizedTariff)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}

package com.taxi.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DriverServiceFilter — AND mantiq, normalizatsiya, null/bo'sh robustligi.
 */
class DriverServiceFilterTest {

    @Test
    @DisplayName("Buyurtmada xizmat yo'q (null/bo'sh) -> har qanday haydovchi mos")
    void noOrderServices_alwaysAccepts() {
        assertTrue(DriverServiceFilter.accepts(Set.of(), null));
        assertTrue(DriverServiceFilter.accepts(Set.of(), ""));
        assertTrue(DriverServiceFilter.accepts(null, "   "));
        assertTrue(DriverServiceFilter.accepts(List.of("AC"), null));
    }

    @Test
    @DisplayName("AND: barcha talab qilingan xizmat yoqilgan bo'lsa mos")
    void allRequiredEnabled_accepts() {
        assertTrue(DriverServiceFilter.accepts(Set.of("ROOF_LUGGAGE", "AC"), "ROOF_LUGGAGE,AC"));
        assertTrue(DriverServiceFilter.accepts(Set.of("ROOF_LUGGAGE", "AC", "DELIVERY"), "AC")); // ortig'i ham mayli
    }

    @Test
    @DisplayName("AND: bitta xizmat yetishmasa mos emas")
    void missingOneRequired_rejects() {
        assertFalse(DriverServiceFilter.accepts(Set.of("AC"), "ROOF_LUGGAGE,AC"));
        assertFalse(DriverServiceFilter.accepts(Set.of("ROOF_LUGGAGE"), "ROOF_LUGGAGE,AC"));
        assertFalse(DriverServiceFilter.accepts(Set.of(), "AC"));
        assertFalse(DriverServiceFilter.accepts(null, "AC"));
    }

    @Test
    @DisplayName("Normalizatsiya: case-insensitive va trim")
    void caseInsensitiveAndTrimmed() {
        assertTrue(DriverServiceFilter.accepts(Set.of("ac", "roof_luggage"), " ROOF_LUGGAGE , AC "));
        assertTrue(DriverServiceFilter.accepts(Set.of("AC"), "ac"));
    }

    @Test
    @DisplayName("parse: CSV -> normalizatsiya qilingan to'plam")
    void parse_normalizes() {
        assertEquals(Set.of("AC", "ROOF_LUGGAGE"), DriverServiceFilter.parse(" ac, roof_luggage "));
        assertEquals(Set.of(), DriverServiceFilter.parse(null));
        assertEquals(Set.of(), DriverServiceFilter.parse(",, "));
    }
}

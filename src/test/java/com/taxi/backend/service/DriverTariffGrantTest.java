package com.taxi.backend.service;

import com.taxi.backend.model.Driver;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** A2 — admin tarif grant'lari mashina-modeli defaultini KENGAYTIRISHINI tekshiradi. */
class DriverTariffGrantTest {

    @Test
    void carDefault_withoutGrant_noKomfort() {
        Set<String> e = DriverTariffFilter.eligibleTariffs("Nexia", null);
        assertTrue(e.contains("STANDART"));
        assertFalse(e.contains("KOMFORT"), "oddiy mashina default KOMFORT bermaydi");
    }

    @Test
    void adminGrant_addsKomfort() {
        Set<String> e = DriverTariffFilter.eligibleTariffs("Nexia", "KOMFORT");
        assertTrue(e.contains("STANDART"));
        assertTrue(e.contains("KOMFORT"), "admin grant KOMFORT qo'shilishi kerak");
    }

    @Test
    void grant_caseAndSpaceInsensitive_multiple() {
        Set<String> e = DriverTariffFilter.eligibleTariffs("Nexia", " komfort , biznes ");
        assertTrue(e.containsAll(Set.of("STANDART", "KOMFORT", "BIZNES")));
    }

    @Test
    void accepts_honorsGrant() {
        Driver d = new Driver();
        d.setCarModel("Nexia");
        d.setTariffGrants("KOMFORT");
        d.setAcceptedTariffs(null); // null → barcha ELIGIBLE tariflarni qabul qiladi (grant effektini izolyatsiya)
        assertTrue(DriverTariffFilter.accepts(d, "KOMFORT"), "grant berilgan + qabul ochiq -> KOMFORT qabul qilinadi");
    }

    @Test
    void accepts_withoutGrant_rejectsKomfort() {
        Driver d = new Driver();
        d.setCarModel("Nexia");
        d.setTariffGrants(null);
        d.setAcceptedTariffs(null);
        assertFalse(DriverTariffFilter.accepts(d, "KOMFORT"), "grantsiz oddiy mashina KOMFORT (fizik) qabul qila olmaydi");
    }

    @Test
    void grant_doesNotBreakCarDefault() {
        Set<String> e = DriverTariffFilter.eligibleTariffs("Chevrolet Malibu 2", null);
        assertTrue(e.contains("KOMFORT"), "Malibu default KOMFORT grant'siz ham bo'lishi kerak");
    }

    @Test
    void legacyEkonom_matchesStandartOrder() {
        // Deploy 3 (A2) — backfilldan o'tib ketgan/eski haydovchi hali 'EKONOM' tutsa ham,
        // normalize() aliasi orqali STANDART buyurtmaga mos kelishi kerak (dispatch buzilmasin).
        Driver d = new Driver();
        d.setCarModel("Nexia");
        d.setAcceptedTariffs("EKONOM,DAMAS");
        assertTrue(DriverTariffFilter.accepts(d, "STANDART"),
                "eski 'EKONOM' token STANDART buyurtmaga mos kelishi kerak (alias)");
    }
}

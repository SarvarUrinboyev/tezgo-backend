package com.taxi.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClickMerchantTransIdTypeTest {

    @Test
    void onlyTheTwoDocumentedNonOverlappingFormsAreClassified() {
        assertEquals(ClickMerchantTransIdType.APP_ORDER_REFERENCE,
                ClickMerchantTransIdType.classify("c2af006c7b11406b9202"));
        assertEquals(ClickMerchantTransIdType.CLICK_CATALOG_ACCOUNT,
                ClickMerchantTransIdType.classify("TZ-0005"));
        assertEquals(ClickMerchantTransIdType.CLICK_CATALOG_ACCOUNT,
                ClickMerchantTransIdType.classify("TZ-12345678901234567"));
    }

    @Test
    void nearMissesNeverFallBackAcrossFlows() {
        for (String value : new String[]{
                null, "", "TZ-005", "tz-0005", "TZ-0005 ", "TZ-0005/evil",
                "TZ-000500000000000000", "C2AF006C7B11406B9202", "c2af006c7b11406b920",
                "c2af006c7b11406b9202x", "g0000000000000000000"}) {
            assertEquals(ClickMerchantTransIdType.INVALID, ClickMerchantTransIdType.classify(value), value);
        }
    }
}

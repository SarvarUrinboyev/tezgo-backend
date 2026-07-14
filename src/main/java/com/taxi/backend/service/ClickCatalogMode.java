package com.taxi.backend.service;

import java.util.Locale;

/** Deployment mode for the whole Click SuperApp catalog flow. */
public enum ClickCatalogMode {
    OFF,
    DRAIN,
    ON;

    static ClickCatalogMode failClosed(String raw) {
        if (raw == null || raw.isBlank()) {
            return OFF;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return OFF;
        }
    }
}

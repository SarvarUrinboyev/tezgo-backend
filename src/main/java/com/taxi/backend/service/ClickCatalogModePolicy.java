package com.taxi.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single authoritative deployment policy for the Click SuperApp catalog flow.
 * An invalid or absent environment value intentionally resolves to OFF.
 */
@Component
public class ClickCatalogModePolicy {

    @Value("${click.superapp.catalog.mode:OFF}")
    private String configuredMode;

    public ClickCatalogMode current() {
        return ClickCatalogMode.failClosed(configuredMode);
    }

    public boolean allowsGetInfo() {
        return current() == ClickCatalogMode.ON;
    }

    public boolean allowsNewPrepare() {
        return current() == ClickCatalogMode.ON;
    }

    /** DRAIN may settle a record that was durable before the drain began. */
    public boolean allowsCompleteForExistingStatus(String status) {
        ClickCatalogMode mode = current();
        if (mode == ClickCatalogMode.ON) {
            return true;
        }
        return mode == ClickCatalogMode.DRAIN
                && ("PREPARED".equals(status) || "CONFIRMED".equals(status));
    }
}

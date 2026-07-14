package com.taxi.backend.service;

import java.util.regex.Pattern;

/**
 * The canonical Click callbacks accept two deliberately non-overlapping forms.
 * App orders are generated one-time references; SuperApp catalog payments carry
 * the reusable public driver account.  Never fall back from one form to the other.
 */
public enum ClickMerchantTransIdType {
    APP_ORDER_REFERENCE,
    CLICK_CATALOG_ACCOUNT,
    INVALID;

    private static final Pattern APP_ORDER_REFERENCE_PATTERN = Pattern.compile("[a-f0-9]{20}");
    // V9 generates TZ- plus at least four digits and drivers.driver_code is VARCHAR(20).
    private static final Pattern CATALOG_ACCOUNT = Pattern.compile("TZ-[0-9]{4,17}");

    public static ClickMerchantTransIdType classify(String merchantTransId) {
        if (merchantTransId == null) {
            return INVALID;
        }
        if (APP_ORDER_REFERENCE_PATTERN.matcher(merchantTransId).matches()) {
            return APP_ORDER_REFERENCE;
        }
        if (CATALOG_ACCOUNT.matcher(merchantTransId).matches()) {
            return CLICK_CATALOG_ACCOUNT;
        }
        return INVALID;
    }

    public static boolean isCatalogAccount(String value) {
        return value != null && CATALOG_ACCOUNT.matcher(value).matches();
    }
}

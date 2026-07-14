package com.taxi.backend.service;

/** TEZGO's proposed GetInfo response codes, pending Click example acceptance. */
public enum ClickGetInfoError {
    SUCCESS(0, "Success"),
    INVALID_ACTION(-3, "INVALID_ACTION"),
    ACCOUNT_NOT_FOUND(-5, "ACCOUNT_NOT_FOUND"),
    MALFORMED_ACCOUNT(-8, "MALFORMED_ACCOUNT"),
    INVALID_SERVICE_ID(-8, "INVALID_SERVICE_ID"),
    TEMPORARY_ERROR(-7, "TEMPORARY_ERROR"),
    RATE_LIMITED(-8, "RATE_LIMITED"),
    UNAUTHORIZED(-1, "UNAUTHORIZED"),
    AUTH_NOT_CONFIGURED(-1, "GETINFO_AUTH_NOT_CONFIGURED"),
    CATALOG_DISABLED(-1, "GETINFO_NOT_ENABLED"),
    MALFORMED_REQUEST(-8, "MALFORMED_REQUEST"),
    ACCOUNT_INACTIVE(-5, "ACCOUNT_NOT_ELIGIBLE");

    private final int code;
    private final String note;

    ClickGetInfoError(int code, String note) {
        this.code = code;
        this.note = note;
    }

    public int code() { return code; }
    public String note() { return note; }
}

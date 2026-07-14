package com.taxi.backend.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Central JSON schema for every GetInfo response, including HTTP parse errors. */
public final class ClickGetInfoResponse {

    private ClickGetInfoResponse() { }

    public static Map<String, Object> error(ClickGetInfoError error) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", error.code());
        response.put("error_note", error.note());
        return response;
    }
}

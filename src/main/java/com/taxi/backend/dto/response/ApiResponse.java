package com.taxi.backend.dto.response;

public record ApiResponse(boolean ok, String message) {
    public static ApiResponse success(String message) {
        return new ApiResponse(true, message);
    }
}

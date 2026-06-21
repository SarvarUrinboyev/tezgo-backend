package com.taxi.backend.dto.response;

public record ServiceResponse(Long id, String serviceType, boolean isActive, Long price) {}

package com.taxi.backend.dto.response;

import java.util.List;

public record AdminDriversPageResponse(
    List<DriverListResponse> content,
    long totalElements,
    int totalPages,
    int size,
    int number,
    long liniyada
) {}

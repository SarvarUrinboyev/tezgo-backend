package com.taxi.backend.dto.response;

import java.util.List;

public record PageResponse<T>(
    List<T> items, int totalPages, long totalElements, int currentPage
) {}

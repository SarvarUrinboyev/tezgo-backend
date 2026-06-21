package com.taxi.backend.dto.response;

public record TransactionResponse(
    Long id, String type, Long amount, String description, String createdAt
) {}

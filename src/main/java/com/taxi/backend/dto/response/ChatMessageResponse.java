package com.taxi.backend.dto.response;

public record ChatMessageResponse(
    String id, Long tripId, Long senderId, String senderName,
    String role, String text, String sentAt, long ts
) {}

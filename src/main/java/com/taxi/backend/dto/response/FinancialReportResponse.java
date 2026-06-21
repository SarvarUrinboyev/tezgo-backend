package com.taxi.backend.dto.response;

public record FinancialReportResponse(
    String date, long completedTrips, long cancelledTrips, long revenue
) {}

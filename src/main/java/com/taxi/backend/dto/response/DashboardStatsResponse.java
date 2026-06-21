package com.taxi.backend.dto.response;

public record DashboardStatsResponse(
    long totalDrivers, long activeDrivers, long pendingDrivers, long onlineDrivers,
    long totalTrips, long todayTrips, long searchingTrips, long todayRevenue
) {}

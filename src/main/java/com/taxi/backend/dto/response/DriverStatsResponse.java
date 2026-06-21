package com.taxi.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DriverStatsResponse(
    long todayTrips, long todayEarnings, long todayNet, long todayCommission,
    long weekTrips, long weekEarnings, long monthTrips, long monthEarnings,
    BigDecimal rating, Integer totalTrips, List<DayStats> daily
) {
    public record DayStats(String date, long trips, long earnings) {}
}

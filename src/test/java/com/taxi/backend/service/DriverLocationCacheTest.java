package com.taxi.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DriverLocationCacheTest {

    private DriverLocationCache cache;

    @BeforeEach
    void setUp() {
        // Mock Redis — testda Redis ishlamaydi, in-memory fallback ishlatiladi
        StringRedisTemplate mockRedis = mock(StringRedisTemplate.class);
        cache = new DriverLocationCache(mockRedis);
    }

    @Test
    void saveAndGetLocation() {
        cache.saveLocation(1L, 41.3, 69.3);
        var loc = cache.getLocation(1L);
        assertNotNull(loc);
        assertEquals(41.3, ((Number) loc.get("lat")).doubleValue());
        assertEquals(69.3, ((Number) loc.get("lon")).doubleValue());
    }

    @Test
    void onlineOffline() {
        cache.setOnline(1L);
        cache.setOnline(2L);
        assertEquals(2, cache.getOnlineDriverIds().size());

        cache.setOffline(1L);
        assertEquals(1, cache.getOnlineDriverIds().size());
        assertNull(cache.getLocation(1L));
    }

    @Test
    void getNearbyDrivers_shouldFilterByRadius() {
        double centerLat = 41.295, centerLon = 69.677;

        cache.setOnline(1L);
        cache.saveLocation(1L, 41.296, 69.678); // ~0.1 km

        cache.setOnline(2L);
        cache.saveLocation(2L, 41.5, 70.0); // ~30+ km

        List<DriverLocationCache.NearbyDriver> nearby = cache.getNearbyDrivers(centerLat, centerLon, 5.0);
        assertEquals(1, nearby.size());
        assertEquals(1L, nearby.get(0).driverId());
    }

    @Test
    void getNearbyDrivers_shouldSortByDistance() {
        double centerLat = 41.295, centerLon = 69.677;

        cache.setOnline(1L);
        cache.saveLocation(1L, 41.30, 69.69); // farther

        cache.setOnline(2L);
        cache.saveLocation(2L, 41.296, 69.678); // closer

        List<DriverLocationCache.NearbyDriver> nearby = cache.getNearbyDrivers(centerLat, centerLon, 50.0);
        assertEquals(2, nearby.size());
        assertEquals(2L, nearby.get(0).driverId()); // closer first
    }

    @Test
    void haversineKm_knownDistance() {
        // Tashkent to Samarkand ~270km
        double dist = DriverLocationCache.haversineKm(41.2995, 69.2401, 39.6542, 66.9597);
        assertTrue(dist > 250 && dist < 300);
    }
}

package com.taxi.backend.service;

import com.taxi.backend.model.Driver;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Scope 1a — operator dispatch (ignoreCooldown=true) cooldown'dagi haydovchini ham topadi. */
@ExtendWith(MockitoExtension.class)
class OperatorDispatchIgnoresCooldownTest {

    @Mock private DriverLocationCache locationCache;
    @Mock private DriverRepository driverRepository;

    private MatchingService matchingService;

    @BeforeEach
    void setup() {
        matchingService = new MatchingService(locationCache, driverRepository);
    }

    /** Bitta yaqin, ONLINE, lekin rad etish cooldown'idagi KOMFORT haydovchi (id=6). */
    private void stubOneCooldownDriver() {
        DriverLocationCache.NearbyDriver nd = new DriverLocationCache.NearbyDriver(6L, 41.29, 69.68, 0.5);
        when(locationCache.getNearbyDrivers(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(nd));
        Driver d = new Driver();
        d.setId(6L);
        d.setOnline(true);
        d.setBalance(0L);
        d.setCarModel("MALIBU");
        d.setAcceptedTariffs("KOMFORT,STANDART");
        d.setActivityScore(0.0);
        d.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(60)); // cooldown FAOL
        User u = new User();
        u.setId(60L);
        u.setName("Haydovchi 6");
        d.setUser(u);
        when(driverRepository.findAllByIdsWithUser(anyList())).thenReturn(List.of(d));
    }

    @Test
    @DisplayName("ignoreCooldown=false -> cooldown'dagi haydovchi CHIQARILADI (yo'lovchi auto-dispatch)")
    void passengerDispatch_excludesCooldown() {
        stubOneCooldownDriver();
        List<MatchingService.MatchedDriver> res = matchingService.findNearbyDrivers(41.29, 69.68, 5.0, false);
        assertTrue(res.isEmpty(), "cooldown'dagi haydovchi yo'lovchi dispatch'da chiqarilishi kerak");
    }

    @Test
    @DisplayName("ignoreCooldown=true -> cooldown'dagi haydovchi KIRADI (operator dispatch)")
    void operatorDispatch_includesCooldown() {
        stubOneCooldownDriver();
        List<MatchingService.MatchedDriver> res = matchingService.findNearbyDrivers(41.29, 69.68, 5.0, true);
        assertEquals(1, res.size(), "operator dispatch cooldown'ni e'tiborsiz qoldirishi kerak");
        assertEquals(6L, res.get(0).driverId());
    }
}

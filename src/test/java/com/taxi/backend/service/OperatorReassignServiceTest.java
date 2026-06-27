package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** P4 — operator reassign-by-driverCode: frees A + excludes A + dispatches B via the EXISTING path. */
@ExtendWith(MockitoExtension.class)
class OperatorReassignServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private AdminService adminService;

    private OperatorReassignService svc;
    private User operator;

    @BeforeEach
    void setup() {
        svc = new OperatorReassignService(tripRepository, driverRepository, adminService);
        operator = new User();
        operator.setId(1L);
        operator.setPhone("+998901234567");
    }

    private Driver driverB(long id, String code, boolean online, long balance, DriverStatus status) {
        Driver d = new Driver();
        d.setId(id);
        d.setDriverCode(code);
        d.setStatus(status);
        d.setOnline(online);
        d.setBalance(balance);
        User u = new User();
        u.setName("Bek");
        d.setUser(u);
        return d;
    }

    private Trip acceptedTripWithDriver(long id, long driverAId) {
        Driver a = new Driver();
        a.setId(driverAId);
        Trip t = new Trip();
        t.setId(id);
        t.setSource("CALL");
        t.setStatus(TripStatus.ACCEPTED);
        t.setDriver(a);
        t.setAcceptedAt(LocalDateTime.now());
        return t;
    }

    @Test
    @DisplayName("Reassign ACCEPTED -> A bo'shatiladi+istisno, trip SEARCHING, B'ga MAVJUD dispatch chaqiriladi")
    void reassignAccepted_freesAndDispatchesB() {
        Trip trip = acceptedTripWithDriver(200L, 5L);
        when(tripRepository.findById(200L)).thenReturn(Optional.of(trip));
        Driver b = driverB(9L, "TZ-9", true, 1000_00L, DriverStatus.ACTIVE);
        when(driverRepository.findByDriverCodeWithUser("TZ-9")).thenReturn(Optional.of(b));
        when(adminService.adminReassignTripToDriver(200L, 9L)).thenReturn(Map.of("offerExpiresAt", 123L));

        Map<String, Object> res = svc.reassignByCode(operator, 200L, "TZ-9");

        assertNull(trip.getDriver(), "A bo'shatilishi kerak");
        assertEquals(TripStatus.SEARCHING, trip.getStatus(), "trip SEARCHING bo'lishi kerak");
        assertTrue(ExcludedDriverFilter.contains(trip.getExcludedDriverIds(), 5L), "A istisno qilinishi kerak");
        verify(adminService).adminReassignTripToDriver(200L, 9L); // MAVJUD data-only dispatch
        assertEquals("TZ-9", res.get("toDriverCode"));
        assertEquals(5L, res.get("freedDriverId"));
        assertEquals("SEARCHING", res.get("status"));
    }

    @Test
    @DisplayName("Reassign — B onlayn emas -> rad, dispatch chaqirilmaydi")
    void reassign_offlineB_rejected() {
        Trip trip = acceptedTripWithDriver(201L, 5L);
        when(tripRepository.findById(201L)).thenReturn(Optional.of(trip));
        Driver b = driverB(9L, "TZ-9", false, 1000_00L, DriverStatus.ACTIVE);
        when(driverRepository.findByDriverCodeWithUser("TZ-9")).thenReturn(Optional.of(b));

        assertThrows(RuntimeException.class, () -> svc.reassignByCode(operator, 201L, "TZ-9"));
        verify(adminService, never()).adminReassignTripToDriver(anyLong(), anyLong());
        verify(tripRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reassign — B balansi manfiy -> rad")
    void reassign_negativeBalanceB_rejected() {
        Trip trip = acceptedTripWithDriver(202L, 5L);
        when(tripRepository.findById(202L)).thenReturn(Optional.of(trip));
        Driver b = driverB(9L, "TZ-9", true, -500L, DriverStatus.ACTIVE);
        when(driverRepository.findByDriverCodeWithUser("TZ-9")).thenReturn(Optional.of(b));

        assertThrows(RuntimeException.class, () -> svc.reassignByCode(operator, 202L, "TZ-9"));
        verify(adminService, never()).adminReassignTripToDriver(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Reassign — B ACTIVE emas -> rad")
    void reassign_nonActiveB_rejected() {
        Trip trip = acceptedTripWithDriver(203L, 5L);
        when(tripRepository.findById(203L)).thenReturn(Optional.of(trip));
        Driver b = driverB(9L, "TZ-9", true, 1000_00L, DriverStatus.BLOCKED);
        when(driverRepository.findByDriverCodeWithUser("TZ-9")).thenReturn(Optional.of(b));

        assertThrows(RuntimeException.class, () -> svc.reassignByCode(operator, 203L, "TZ-9"));
        verify(adminService, never()).adminReassignTripToDriver(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Reassign — STARTED safar -> rad (avval bekor qilish kerak)")
    void reassign_startedTrip_rejected() {
        Trip trip = acceptedTripWithDriver(204L, 5L);
        trip.setStatus(TripStatus.STARTED);
        when(tripRepository.findById(204L)).thenReturn(Optional.of(trip));

        assertThrows(RuntimeException.class, () -> svc.reassignByCode(operator, 204L, "TZ-9"));
        verify(driverRepository, never()).findByDriverCodeWithUser(any());
        verify(adminService, never()).adminReassignTripToDriver(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Reassign — driverCode topilmadi -> rad")
    void reassign_driverNotFound_rejected() {
        Trip trip = acceptedTripWithDriver(205L, 5L);
        when(tripRepository.findById(205L)).thenReturn(Optional.of(trip));
        when(driverRepository.findByDriverCodeWithUser("TZ-X")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> svc.reassignByCode(operator, 205L, "TZ-X"));
        verify(adminService, never()).adminReassignTripToDriver(anyLong(), anyLong());
    }
}

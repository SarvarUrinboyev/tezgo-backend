package com.taxi.backend.service;

import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Scope 3b — umumiy clearDriverAssignment haydovchini to'liq bo'shatadi. */
class TripAssignmentUtilTest {

    @Test
    @DisplayName("clearDriverAssignment -> driver/accepted/broadcast/notified tozalanadi (haydovchi bo'shaydi)")
    void clearsAll() {
        Driver d = new Driver();
        d.setId(5L);
        Trip t = new Trip();
        t.setDriver(d);
        t.setAcceptedAt(LocalDateTime.now());
        t.setBroadcastAt(LocalDateTime.now());
        t.setNotifiedDriverIds("5,6");

        TripAssignmentUtil.clearDriverAssignment(t);

        assertNull(t.getDriver(), "driver bo'shatilishi kerak (busy-set'dan chiqadi)");
        assertNull(t.getAcceptedAt());
        assertNull(t.getBroadcastAt());
        assertNull(t.getNotifiedDriverIds());
    }
}

package com.taxi.backend.service;

import com.taxi.backend.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BroadcastBusyGuardTest {
    @Test
    void sharedBoardClaimIsAlwaysRejected() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> new BroadcastBoardService().claimTrip(new User(), 1L));
        assertEquals(409, error.getStatusCode().value());
    }
}

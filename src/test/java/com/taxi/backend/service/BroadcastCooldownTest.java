package com.taxi.backend.service;

import com.taxi.backend.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BroadcastCooldownTest {
    @Test
    void cooldownCannotBecomeAnAlternateSharedBoardAcceptancePath() {
        assertThrows(ResponseStatusException.class,
                () -> new BroadcastBoardService().claimTrip(new User(), 1L));
    }
}

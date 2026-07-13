package com.taxi.backend.service;

import com.taxi.backend.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BroadcastBalanceGateTest {
    @Test
    void generalBoardIsDisabledRegardlessOfBalance() {
        assertTrue(new BroadcastBoardService().getBroadcastBoard(new User()).isEmpty());
    }
}

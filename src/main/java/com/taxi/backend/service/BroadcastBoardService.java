package com.taxi.backend.service;

import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Retained only for controller compatibility. SEARCHING trips no longer become
 * an actionable shared board; the driver's normal offer/poll endpoint is the
 * sole source of an actionable offer.
 */
@Service
public class BroadcastBoardService {

    @Autowired
    public BroadcastBoardService() {
    }

    /** Test/source compatibility only. The dependencies no longer own a dispatch path. */
    @Deprecated
    public BroadcastBoardService(TripRepository tripRepository,
                                 DriverRepository driverRepository,
                                 PushNotificationService pushService) {
        this();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBroadcastBoard(User driverUser) {
        return List.of();
    }

    @Transactional
    public Map<String, Object> claimTrip(User driverUser, Long tripId) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Umumiy buyurtmalar taxtasi o'chirilgan; faqat sizga yuborilgan taklifni qabul qiling");
    }
}

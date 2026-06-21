package com.taxi.backend.service;

import com.taxi.backend.model.Trip;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Mijoz tarixini telefon raqami orqali qidirish.
 *
 * Operator yangi buyurtma yaratayotganda — mijoz raqamini kiritadi,
 * tizim oxirgi safar manzillarini avtomatik taklif qiladi.
 */
@Service
public class PassengerHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PassengerHistoryService.class);

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    public PassengerHistoryService(TripRepository tripRepository,
                                   UserRepository userRepository) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
    }

    /**
     * Mijoz tarixini telefon raqami bo'yicha qidirish.
     *
     * @param phone +998XXXXXXXXX formatda
     * @return {found: true/false, ...} — oxirgi safar ma'lumotlari
     */
    @Transactional(readOnly = true)
    public Map<String, Object> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return Map.of("found", false);
        }

        // Foydalanuvchini topish
        Optional<com.taxi.backend.model.User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isEmpty()) {
            return Map.of("found", false);
        }

        com.taxi.backend.model.User user = userOpt.get();

        // Oxirgi CALL trip ni qidirish
        Optional<Trip> lastTripOpt = tripRepository.findLastCallTripByPassengerId(user.getId());
        if (lastTripOpt.isEmpty()) {
            return Map.of("found", false);
        }

        Trip lastTrip = lastTripOpt.get();

        // Jami trip soni (barcha source)
        long totalTrips = tripRepository.countByPassengerId(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("found", true);
        result.put("passengerName", user.getName() != null ? user.getName() : "Noma'lum");
        result.put("totalTrips", totalTrips);
        result.put("lastPickup", lastTrip.getFromAddress() != null ? lastTrip.getFromAddress() : "");
        result.put("lastDestination", lastTrip.getToAddress() != null ? lastTrip.getToAddress() : "");
        result.put("lastTripDate", lastTrip.getCreatedAt() != null
                ? lastTrip.getCreatedAt().toLocalDate().toString() : "");
        return result;
    }
}

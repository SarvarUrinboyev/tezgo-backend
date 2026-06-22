package com.taxi.backend.service;

import com.taxi.backend.model.Trip;

/**
 * Trip'dan haydovchi biriktirilishi + kutish/broadcast holatini tozalash — umumiy yordamchi.
 *
 * Bir nechta joyda kerak (haydovchi bekor qilishi, operator bekor qilishi, scheduler backstop):
 * tripni terminal/CANCELLED qilgandan so'ng driver_id ni NULL qilsa, haydovchi
 * ACTIVE_DRIVER_STATUSES "band" to'plamidan chiqadi — ya'ni yangi buyurtma ola oladi (bo'shaydi).
 * Sof trip-mutatsiya (repo/DB chaqiruvisiz) — saqlash chaqiruvchining zimmasida.
 */
public final class TripAssignmentUtil {

    private TripAssignmentUtil() {}

    /** Haydovchini bo'shatadi: driver + accepted/arrived/waiting/broadcast/notified holati tozalanadi. */
    public static void clearDriverAssignment(Trip trip) {
        trip.setDriver(null);
        trip.setAcceptedAt(null);
        trip.setArrivedAt(null);
        trip.setWaitingStartedAt(null);
        trip.setWaitingEndedAt(null);
        trip.setWaitingPrice(0L);
        trip.setBroadcastAt(null);
        trip.setNotifiedDriverIds(null);
    }
}

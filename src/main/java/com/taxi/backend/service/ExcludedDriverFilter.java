package com.taxi.backend.service;

import java.util.HashSet;
import java.util.Set;

/**
 * Tripdan chiqarilgan haydovchilar (excluded_driver_ids) — CSV utility.
 *
 * Haydovchi tripni bekor qilganda uning IDsi excluded_driver_ids ga qo'shiladi.
 * Shundan keyin u shu tripni qayta YO'Q ko'rmasligi va qabul qila olmasligi kerak:
 *   - matching push (TripNotificationHelper)
 *   - broadcast push (TripExpiryScheduler)
 *   - broadcast taxta ro'yxati (BroadcastBoardService.getBroadcastBoard)
 *   - mavjud buyurtmalar ro'yxati (TripService.getAvailableTrips)
 *   - claim / accept (BroadcastBoardService.claimTrip, TripService.acceptTrip)
 *
 * notified_driver_ids dedup uchun (har matching da qayta yoziladi); bu ro'yxat esa
 * bekor qilishlar davomida saqlanadi. Shu sabab alohida ustun va alohida utility.
 */
public final class ExcludedDriverFilter {

    private ExcludedDriverFilter() {}

    /** CSV ni haydovchi IDlari to'plamiga aylantirish. null/bo'sh → bo'sh to'plam. */
    public static Set<Long> parse(String csv) {
        Set<Long> ids = new HashSet<>();
        if (csv == null || csv.isBlank()) return ids;
        for (String s : csv.split(",")) {
            try {
                ids.add(Long.parseLong(s.trim()));
            } catch (NumberFormatException ignored) {
                // noto'g'ri qiymatni o'tkazib yuborish
            }
        }
        return ids;
    }

    /** Haydovchi chiqarilganmi? */
    public static boolean contains(String csv, Long driverId) {
        if (driverId == null) return false;
        return parse(csv).contains(driverId);
    }

    /** Haydovchini ro'yxatga qo'shib yangilangan CSV qaytarish (takrorlanmaydi). */
    public static String append(String csv, Long driverId) {
        if (driverId == null) return csv;
        Set<Long> ids = parse(csv);
        ids.add(driverId);
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        return sb.toString();
    }
}

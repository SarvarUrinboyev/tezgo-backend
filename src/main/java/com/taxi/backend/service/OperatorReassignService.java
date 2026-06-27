package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * P4 — operator buyurtmani driverCode bo'yicha boshqa haydovchiga beradi.
 * Alohida service: OperatorService konstruktori o'zgarmasligi uchun.
 *
 * ACCEPTED trip uchun yagona yangi orkestratsiya — FAQAT mavjud mexanizmlardan:
 *   1. Haydovchi B tekshiriladi (mavjud / ACTIVE / online / balans>=0 / trip reassignable).
 *   2. Haydovchi A MAVJUD ExcludedDriverFilter bilan istisno qilinadi + MAVJUD
 *      TripAssignmentUtil.clearDriverAssignment bilan bo'shatiladi. A'ning ilovasi
 *      o'zining aktiv-trip pollingi orqali safardan chiqqanini ko'radi (mavjud
 *      cancellation/poll yo'li) — qo'lda push QURILMAYDI.
 *   3. Trip = SEARCHING, so'ng MAVJUD AdminService.adminReassignTripToDriver B'ga
 *      DISPATCH qiladi — bu MAVJUD DATA-ONLY ORDER_PUSH'ni O'ZGARTIRMASDAN yuboradi.
 * Frozen core (5 FSI fayl, push shakli, narx formulasi, dispatch ichi) TEGILMAYDI.
 */
@Service
public class OperatorReassignService {

    private static final Logger log = LoggerFactory.getLogger(OperatorReassignService.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("OPERATOR_AUDIT");

    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final AdminService adminService;

    public OperatorReassignService(TripRepository tripRepository,
                                   DriverRepository driverRepository,
                                   AdminService adminService) {
        this.tripRepository = tripRepository;
        this.driverRepository = driverRepository;
        this.adminService = adminService;
    }

    @Transactional
    public Map<String, Object> reassignByCode(User operator, Long tripId, String driverCode) {
        if (driverCode == null || driverCode.isBlank()) {
            throw new RuntimeException("Haydovchi kodi bo'sh bo'lmasligi kerak");
        }
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));

        // --- trip reassignable? non-terminal va STARTED emas (safar davomida ko'chirilmaydi) ---
        TripStatus st = trip.getStatus();
        if (TripStatus.TERMINAL_STATUSES.contains(st)) {
            throw new RuntimeException("Buyurtma yakunlangan/bekor qilingan — ko'chirib bo'lmaydi");
        }
        if (st == TripStatus.STARTED) {
            throw new RuntimeException("Safar boshlangan — boshqa haydovchiga berib bo'lmaydi (avval bekor qiling)");
        }

        // --- haydovchi B'ni topish + tekshirish (mavjud zero-balance qoidasi) ---
        Driver b = driverRepository.findByDriverCodeWithUser(driverCode.trim())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi: " + driverCode));
        if (b.getStatus() != DriverStatus.ACTIVE) {
            throw new RuntimeException("Haydovchi ACTIVE emas (status=" + b.getStatus() + ")");
        }
        if (!b.isOnline()) {
            throw new RuntimeException("Haydovchi onlayn emas");
        }
        if (b.getBalance() != null && b.getBalance() < 0) {
            throw new RuntimeException("Haydovchi balansi manfiy — buyurtma berilmaydi");
        }
        Long a = trip.getDriver() != null ? trip.getDriver().getId() : null;
        if (a != null && a.equals(b.getId())) {
            throw new RuntimeException("Buyurtma allaqachon shu haydovchida");
        }

        // --- A'ni istisno qilish + bo'shatish, trip = SEARCHING (MAVJUD mexanizmlar) ---
        if (a != null) {
            // A istisno: re-dispatch da yana A'ga taklif ketmasin (mavjud cancellation-redispatch qoidasi)
            trip.setExcludedDriverIds(ExcludedDriverFilter.append(trip.getExcludedDriverIds(), a));
            // A bo'shatiladi — clearDriverAssignment cooldown/cancelCount'ga TEGMAYDI (A jazolanmaydi).
            TripAssignmentUtil.clearDriverAssignment(trip);
            trip.setStatus(TripStatus.SEARCHING);
            tripRepository.save(trip);
            log.info("[OPERATOR] Reassign #{}: A={} bo'shatildi+istisno -> SEARCHING", tripId, a);
        } else if (st != TripStatus.SEARCHING) {
            trip.setStatus(TripStatus.SEARCHING);
            tripRepository.save(trip);
        }

        // --- B'ga DISPATCH: MAVJUD data-only ORDER_PUSH yo'li (notifyDriver(id,null,null,data), type=ORDER_PUSH).
        //     Yangi push QURILMAYDI — adminReassignTripToDriver qayta ishlatiladi. ---
        Map<String, Object> dispatch = adminService.adminReassignTripToDriver(tripId, b.getId());

        AUDIT.info("[AUDIT][OPERATOR] action=REASSIGN operator={} operatorId={} tripId={} prevStatus={} fromDriverId={} toDriverCode={} toDriverId={} aPenalty=none",
                operator.getPhone(), operator.getId(), tripId, st.name(), a, b.getDriverCode(), b.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("tripId", tripId);
        result.put("freedDriverId", a);
        result.put("toDriverCode", b.getDriverCode());
        result.put("toDriverId", b.getId());
        result.put("toDriverName", b.getUser() != null ? b.getUser().getName() : null);
        result.put("offerExpiresAt", dispatch.get("offerExpiresAt"));
        result.put("status", "SEARCHING");
        result.put("message", "Buyurtma " + b.getDriverCode() + " haydovchiga yuborildi");
        return result;
    }
}

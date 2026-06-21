package com.taxi.backend.service;

import com.taxi.backend.model.Trip;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Trip Notification Helper — buyurtma notification logikasini TripService dan ajratish.
 *
 * Vazifasi:
 *   - Yangi buyurtma kelganda yaqin haydovchilarga xabar yuborish
 *   - Matching engine + fallback (kam haydovchi bo'lsa barcha online larga)
 *   - Barcha notification ASYNC — blocking I/O yo'q
 */
@Component
public class TripNotificationHelper {

    private static final Logger log = LoggerFactory.getLogger(TripNotificationHelper.class);

    private final MatchingService matchingService;
    private final DriverRepository driverRepository;
    private final AsyncNotificationService asyncNotifier;
    private final TripRepository tripRepository;

    @Value("${matching.radius-km:5.0}")
    private double matchingRadiusKm;

    public TripNotificationHelper(MatchingService matchingService,
                                   DriverRepository driverRepository,
                                   AsyncNotificationService asyncNotifier,
                                   TripRepository tripRepository) {
        this.matchingService = matchingService;
        this.driverRepository = driverRepository;
        this.asyncNotifier = asyncNotifier;
        this.tripRepository = tripRepository;
    }

    /** Yaqin haydovchilarga yangi buyurtma haqida xabar */
    public void notifyNearbyDrivers(Trip trip) {
        double lat = trip.getFromLat() != null ? trip.getFromLat() : 0.0;
        double lon = trip.getFromLon() != null ? trip.getFromLon() : 0.0;
        String tariffName = trip.getTariff() != null && trip.getTariff().getName() != null
                ? trip.getTariff().getName().toUpperCase() : "EKONOM";

        log.info("[MATCHING] Buyurtma #{} uchun haydovchi qidirilmoqda (lat={}, lon={}, radius={}km, tariff={})",
                trip.getId(), lat, lon, matchingRadiusKm, tariffName);

        List<MatchingService.MatchedDriver> candidates = List.of();
        try {
            candidates = matchingService.findNearbyDrivers(lat, lon, matchingRadiusKm);
        } catch (Exception e) {
            log.error("[MATCHING] Matching engine xatolik — fallback ga o'tish: {}", e.getMessage(), e);
        }

        // Bekor qilgan (chiqarilgan) haydovchilar — qayta yuborishda ularga taklif qilinmaydi
        Set<Long> excluded = ExcludedDriverFilter.parse(trip.getExcludedDriverIds());
        // Band haydovchilar (faol tripi bor) — yangi buyurtma olmaydi
        Set<Long> busy = new HashSet<>(
                tripRepository.findBusyDriverIds(com.taxi.backend.enums.TripStatus.ACTIVE_DRIVER_STATUSES));

        // Service hard-filter: buyurtma xizmatlari bo'lsa, ularning barchasiga ega haydovchilargina
        String selectedServices = trip.getSelectedServices();
        boolean serviceFilter = selectedServices != null && !selectedServices.isBlank();
        Map<Long, Set<String>> enabledByDriver = serviceFilter
                ? enabledServicesByDriverIds(candidates.stream()
                        .map(MatchingService.MatchedDriver::driverId).collect(Collectors.toList()))
                : Map.of();

        Set<Long> notifiedIds = new HashSet<>();
        for (MatchingService.MatchedDriver c : candidates) {
            if (excluded.contains(c.driverId())) continue;
            if (busy.contains(c.driverId())) continue;
            if (!driverAcceptsTariff(c.carModel(), c.acceptedTariffs(), c.tariffGrants(), tariffName)) continue;
            if (!DriverServiceFilter.accepts(enabledByDriver.get(c.driverId()), selectedServices)) continue;
            sendNewOrderNotification(c.driverId(), trip, (int) Math.ceil(c.etaMinutes()));
            notifiedIds.add(c.driverId());
        }

        log.info("[MATCHING] Topildi: {} ta yaqin haydovchi, xabar yuborildi: {} ta",
                candidates.size(), notifiedIds.size());

        // FALLBACK: kam haydovchi topilsa barcha online larga (Parkent kichik tuman)
        if (candidates.size() < 3) {
            List<com.taxi.backend.model.Driver> online = driverRepository.findByIsOnlineTrue();
            Map<Long, Set<String>> fbEnabled = serviceFilter
                    ? enabledServicesByDriverIds(online.stream()
                            .map(com.taxi.backend.model.Driver::getId).collect(Collectors.toList()))
                    : Map.of();
            online.forEach(d -> {
                if (!notifiedIds.contains(d.getId())) {
                    if (excluded.contains(d.getId())) return; // bekor qilgan haydovchi — skip
                    if (busy.contains(d.getId())) return; // faol tripi bor — skip
                    if (d.isInCooldown()) return; // rad etish cooldown'i — skip
                    if (!driverAcceptsTariff(d.getCarModel(), d.getAcceptedTariffs(), d.getTariffGrants(), tariffName)) return;
                    if (d.getBalance() != null && d.getBalance() < 0) return; // Manfiy balans — skip
                    if (!DriverServiceFilter.accepts(fbEnabled.get(d.getId()), selectedServices)) return; // xizmat mos emas
                    sendNewOrderNotification(d.getId(), trip, -1);
                    notifiedIds.add(d.getId());
                }
            });
        }

        // Xabardor qilingan haydovchilar IDlarini tripga saqlash —
        // broadcast fazasida ular skip qilinadi (ikki marta xabardor bo'lmasligi uchun)
        if (!notifiedIds.isEmpty()) {
            trip.setNotifiedDriverIds(
                    notifiedIds.stream().map(String::valueOf).collect(Collectors.joining(","))
            );
            tripRepository.save(trip);
        }
    }

    /** Bir nechta haydovchining yoqilgan xizmatlari — Map<driverId, {ServiceType kodlari}> (batch, N+1 fix). */
    private Map<Long, Set<String>> enabledServicesByDriverIds(java.util.Collection<Long> driverIds) {
        if (driverIds.isEmpty()) return Map.of();
        Map<Long, Set<String>> map = new HashMap<>();
        for (Object[] row : driverRepository.findEnabledServiceRowsByDriverIds(driverIds)) {
            Long id = (Long) row[0];
            com.taxi.backend.enums.ServiceType st = (com.taxi.backend.enums.ServiceType) row[1];
            map.computeIfAbsent(id, k -> new HashSet<>()).add(st.name());
        }
        return map;
    }

    /** Haydovchi berilgan tarifni qabul qilishini tekshirish */
    private boolean driverAcceptsTariff(String carModel, String acceptedTariffs, String tariffGrants, String tariffName) {
        com.taxi.backend.model.Driver driver = new com.taxi.backend.model.Driver();
        // MUHIM: carModel ham o'rnatilishi SHART — DriverTariffFilter avval mashina modeliga
        // qarab eligibleTariffs ni hisoblaydi. carModel=null bo'lsa faqat {STANDART} chiqadi va
        // KOMFORT/DAMAS/ELECTRO/BIZNES buyurtmalar HECH KIMGA yuborilmaydi.
        // tariffGrants — admin qo'lda bergan tariflar (A2) ham hisobga olinadi.
        driver.setCarModel(carModel);
        driver.setAcceptedTariffs(acceptedTariffs);
        driver.setTariffGrants(tariffGrants);
        return DriverTariffFilter.accepts(driver, tariffName);
    }

    /** Buyurtma taklifi haydovchiga ochiq turadigan vaqt (ms) — WS va FCM uchun AYNAN bir xil deadline. */
    private static final long OFFER_TTL_MS = 15_000L;

    private void sendNewOrderNotification(Long driverId, Trip trip, int etaMinutes) {
        // Har yetkazish uchun YANGI deadline (hozir + 15s). Bir xil qiymat WS NEW_ORDER va FCM ORDER_PUSH
        // ga ketadi → frontend/native timer SERVER vaqtiga bog'lanadi (trip.createdAt ISHLATILMAYDI).
        long offerExpiresAt = System.currentTimeMillis() + OFFER_TTL_MS;

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "NEW_ORDER");
        msg.put("tripId", trip.getId());
        msg.put("fromAddress", trip.getFromAddress());
        msg.put("toAddress", trip.getToAddress() != null ? trip.getToAddress() : "—");
        msg.put("price", trip.getTotalPrice() / 100);
        msg.put("offerExpiresAt", offerExpiresAt);
        if (etaMinutes >= 0) msg.put("etaMinutes", etaMinutes);

        asyncNotifier.notifyDriverAsync(driverId, msg);

        // PUSH (high-priority, DATA-ONLY) — buyurtma app YOPIQ / fon / QULFLANGAN bo'lsa ham yetadi.
        // type="ORDER_PUSH" ni native TezgoMessagingService ushlaydi va to'liq-ekran "kiruvchi
        // qo'ng'iroq" bildirishnomasini (FSI) chiqaradi — FGS-start chekloviga tushmaydi (v1.1.0 darsi).
        // PushNotificationService order push'ni qat'iy data-only yuboradi; title/body berilmaydi.
        String toAddr = trip.getToAddress() != null ? trip.getToAddress() : "manzilsiz";
        String fromAddr = trip.getFromAddress() != null ? trip.getFromAddress() : "manzilsiz";
        long priceSom = (trip.getTotalPrice() != null ? trip.getTotalPrice() : 0L) / 100;
        Map<String, Object> pushData = new HashMap<>();
        pushData.put("tripId", trip.getId());
        pushData.put("type", "ORDER_PUSH");
        pushData.put("event", "ORDER_PUSH");
        pushData.put("fromAddress", fromAddr);
        pushData.put("toAddress", toAddr);
        pushData.put("price", priceSom);
        if (etaMinutes >= 0) pushData.put("etaMinutes", etaMinutes);
        // Koordinatalar — IncomingOrderModal xaritasi (haydovchi→olib ketish yo'l chizig'i) uchun
        pushData.put("fromLat", trip.getFromLat() != null ? trip.getFromLat() : 0.0);
        pushData.put("fromLon", trip.getFromLon() != null ? trip.getFromLon() : 0.0);
        pushData.put("toLat", trip.getToLat() != null ? trip.getToLat() : 0.0);
        pushData.put("toLon", trip.getToLon() != null ? trip.getToLon() : 0.0);
        // Tarif nomi + "chaqiruv/yetib borish summasi" (= tarif baza summasi, tiyinda) + createdAt —
        // IncomingOrderModal jami narx o'rniga shu chaqiruv summasini ko'rsatadi, timer createdAt'ga
        // bog'lanadi. Tarif LAZY — guard bilan o'qiymiz (yuklanmasa fallback 0 + log).
        try {
            if (trip.getTariff() != null) {
                pushData.put("tariffName", trip.getTariff().getName());
                Long base = trip.getTariff().getBasePrice();
                pushData.put("calloutFee", base != null ? base : 0L);
            }
        } catch (Exception e) {
            log.debug("push tariff lazy load xato (tripId={}): {}", trip.getId(), e.getMessage());
        }
        pushData.put("offerExpiresAt", offerExpiresAt); // WS bilan AYNAN bir xil deadline (epoch ms)
        asyncNotifier.pushDriverAsync(driverId,
                null,
                null,
                pushData);
    }
}

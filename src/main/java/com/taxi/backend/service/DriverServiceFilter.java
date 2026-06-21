package com.taxi.backend.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Driver extra-service eligibility matcher.
 *
 * Buyurtmaning tanlangan xizmatlari (trips.selected_services — ServiceType kodlari, vergul bilan)
 * haydovchining yoqilgan xizmatlari (driver_services.is_enabled = true) bilan solishtiriladi.
 *
 * QAT'IY AND mantiq: buyurtma talab qilgan HAR BIR xizmat haydovchida yoqilgan bo'lishi shart.
 * Bittasi yetishmasa — haydovchi mos emas (match/notify/board/claim/accept rad etiladi).
 *
 * DriverTariffFilter bilan bir xil uslub — bir xil normalizatsiya (trim + uppercase) hamma joyda.
 */
public final class DriverServiceFilter {

    private DriverServiceFilter() {}

    /** Order CSV → normalizatsiya qilingan kodlar to'plami. null/bo'sh → bo'sh to'plam. */
    public static Set<String> parse(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(DriverServiceFilter::normalize)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * Haydovchi buyurtmaning barcha tanlangan xizmatlariga ega bo'lsa true.
     *
     * @param driverEnabledServices haydovchida yoqilgan xizmat kodlari (ServiceType.name())
     * @param orderSelectedServicesCsv buyurtma tanlagan xizmatlar (CSV)
     * @return buyurtmada xizmat yo'q bo'lsa true; aks holda har bir talab qilingan xizmat
     *         haydovchida yoqilgan bo'lsa (AND) true
     */
    public static boolean accepts(Collection<String> driverEnabledServices, String orderSelectedServicesCsv) {
        Set<String> required = parse(orderSelectedServicesCsv);
        if (required.isEmpty()) return true; // xizmatsiz buyurtma — bu filtr hammaga ochiq
        if (driverEnabledServices == null || driverEnabledServices.isEmpty()) return false;
        Set<String> enabled = driverEnabledServices.stream()
                .map(DriverServiceFilter::normalize)
                .collect(Collectors.toSet());
        return enabled.containsAll(required); // AND — barcha talab qilingan xizmat yoqilgan
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}

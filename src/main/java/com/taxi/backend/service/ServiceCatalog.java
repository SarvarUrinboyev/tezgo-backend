package com.taxi.backend.service;

import com.taxi.backend.enums.ServiceType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Qo'shimcha xizmatlar katalogi va CSV yordamchilari (ServiceType asosida).
 *
 * Narxlar tiyinda (ServiceType.getDefaultPriceTiyin). Trip da tanlangan xizmatlar
 * vergul bilan ajratilgan kodlar sifatida saqlanadi (selected_services), umumiy narxi
 * esa extra_price (tiyin) da.
 */
public final class ServiceCatalog {

    private ServiceCatalog() {}

    /** Request kodlari ro'yxatini validatsiya qilingan, takrorlanmaydigan ServiceType ro'yxatiga. */
    public static List<ServiceType> parse(List<String> codes) {
        List<ServiceType> out = new ArrayList<>();
        if (codes == null) return out;
        for (String c : codes) addUnique(out, toType(c));
        return out;
    }

    /** CSV ("REAR_LUGGAGE,AC") ni ServiceType ro'yxatiga. */
    public static List<ServiceType> parseCsv(String csv) {
        List<ServiceType> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String c : csv.split(",")) addUnique(out, toType(c));
        return out;
    }

    /** ServiceType ro'yxatini vergulli CSV ga (bo'sh bo'lsa null). */
    public static String csv(Collection<ServiceType> services) {
        if (services == null || services.isEmpty()) return null;
        return services.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    /** Tanlangan xizmatlarning umumiy narxi (tiyin). */
    public static long totalTiyin(Collection<ServiceType> services) {
        if (services == null) return 0L;
        return services.stream().mapToLong(ServiceType::getDefaultPriceTiyin).sum();
    }

    /** [{code, name, price(tiyin)}] ro'yxati — API va UI uchun. */
    public static List<Map<String, Object>> toDto(Collection<ServiceType> services) {
        if (services == null) return List.of();
        return services.stream()
                .map(st -> Map.<String, Object>of(
                        "code", st.name(),
                        "name", st.getUzName(),
                        "price", st.getDefaultPriceTiyin()))
                .collect(Collectors.toList());
    }

    /** Butun katalog (barcha ServiceType). */
    public static List<Map<String, Object>> catalog() {
        return toDto(List.of(ServiceType.values()));
    }

    private static void addUnique(List<ServiceType> list, ServiceType st) {
        if (st != null && !list.contains(st)) list.add(st);
    }

    private static ServiceType toType(String code) {
        if (code == null) return null;
        try {
            return ServiceType.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null; // noto'g'ri kodni o'tkazib yuborish
        }
    }
}

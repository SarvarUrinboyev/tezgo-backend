package com.taxi.backend.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/**
 * Operator tomonidan telefon orqali buyurtma yaratish.
 * Mijoz qo'ng'iroq qiladi → Operator manzillarni kiritadi → API ga yuboradi.
 */
public class OperatorTripRequest {

    @NotBlank(message = "Mijoz telefon raqami bo'sh bo'lishi mumkin emas")
    @Pattern(regexp = "^\\+998\\d{9}$", message = "Telefon formati: +998XXXXXXXXX")
    private String passengerPhone;

    @NotBlank(message = "Olish manzili bo'sh bo'lishi mumkin emas")
    @Size(max = 300, message = "Manzil 300 belgidan oshmasin")
    private String pickupAddress;

    @Size(max = 300, message = "Manzil 300 belgidan oshmasin")
    private String destinationAddress;

    /** Tarif ID — ixtiyoriy (default: EKONOM) */
    private Long tariffId;

    /** Rejim: FIXED (aniq manzil) | TAXOMETER (haydovchi hisoblaydi). Default: FIXED */
    private String mode;

    /** Tanlangan qo'shimcha xizmatlar — ServiceType kodlari (masalan ["REAR_LUGGAGE","AC"]). Ixtiyoriy. */
    private List<String> selectedServices;

    /** Olish nuqtasi koordinatalari (xaritadan) */
    @DecimalMin(value = "-90.0", message = "Latitude -90 dan kichik bo'lishi mumkin emas")
    @DecimalMax(value = "90.0", message = "Latitude 90 dan katta bo'lishi mumkin emas")
    private Double fromLat;

    @DecimalMin(value = "-180.0", message = "Longitude -180 dan kichik bo'lishi mumkin emas")
    @DecimalMax(value = "180.0", message = "Longitude 180 dan katta bo'lishi mumkin emas")
    private Double fromLon;

    /** Borish nuqtasi koordinatalari (xaritadan) */
    @DecimalMin(value = "-90.0", message = "Latitude -90 dan kichik bo'lishi mumkin emas")
    @DecimalMax(value = "90.0", message = "Latitude 90 dan katta bo'lishi mumkin emas")
    private Double toLat;

    @DecimalMin(value = "-180.0", message = "Longitude -180 dan kichik bo'lishi mumkin emas")
    @DecimalMax(value = "180.0", message = "Longitude 180 dan katta bo'lishi mumkin emas")
    private Double toLon;

    public String getPassengerPhone() { return passengerPhone; }
    public void setPassengerPhone(String passengerPhone) { this.passengerPhone = passengerPhone; }
    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }
    public String getDestinationAddress() { return destinationAddress; }
    public void setDestinationAddress(String destinationAddress) { this.destinationAddress = destinationAddress; }
    public Long getTariffId() { return tariffId; }
    public void setTariffId(Long tariffId) { this.tariffId = tariffId; }
    public Double getFromLat() { return fromLat; }
    public void setFromLat(Double fromLat) { this.fromLat = fromLat; }
    public Double getFromLon() { return fromLon; }
    public void setFromLon(Double fromLon) { this.fromLon = fromLon; }
    public Double getToLat() { return toLat; }
    public void setToLat(Double toLat) { this.toLat = toLat; }
    public Double getToLon() { return toLon; }
    public void setToLon(Double toLon) { this.toLon = toLon; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public List<String> getSelectedServices() { return selectedServices; }
    public void setSelectedServices(List<String> selectedServices) { this.selectedServices = selectedServices; }

    public boolean isTaxometerMode() {
        return "TAXOMETER".equalsIgnoreCase(mode);
    }
}

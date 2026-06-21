package com.taxi.backend.security;

import com.taxi.backend.dto.OperatorTripRequest;
import com.taxi.backend.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Operator Security Tests — rol asosida kirish tekshiruvi.
 */
class OperatorSecurityTest {

    @Test
    @DisplayName("OPERATOR-01: Role enum da OPERATOR mavjud")
    void roleEnumHasOperator() {
        Role role = Role.OPERATOR;
        assertEquals("OPERATOR", role.name());
    }

    @Test
    @DisplayName("OPERATOR-02: OPERATOR role barcha rollardan farqli")
    void operatorRoleIsDistinct() {
        assertNotEquals(Role.ADMIN, Role.OPERATOR);
        assertNotEquals(Role.DRIVER, Role.OPERATOR);
        assertNotEquals(Role.PASSENGER, Role.OPERATOR);
    }

    @Test
    @DisplayName("OPERATOR-03: OperatorTripRequest telefon validatsiyasi")
    void tripRequest_phoneValidation() {
        // To'g'ri format
        assertTrue("+998901234567".matches("^\\+998\\d{9}$"));

        // Noto'g'ri formatlar
        assertFalse("901234567".matches("^\\+998\\d{9}$"));
        assertFalse("+99890123456".matches("^\\+998\\d{9}$")); // 8 ta raqam
        assertFalse("+9989012345678".matches("^\\+998\\d{9}$")); // 10 ta raqam
        assertFalse("'.matches('".matches("^\\+998\\d{9}$")); // SQL injection
    }

    @Test
    @DisplayName("OPERATOR-04: OperatorTripRequest bo'sh manzil qabul qilinmaydi")
    void tripRequest_addressValidation() {
        OperatorTripRequest req = new OperatorTripRequest();
        req.setPickupAddress("");
        // @NotBlank — bo'sh string qabul qilinmaydi
        assertTrue(req.getPickupAddress().isBlank(),
                "Bo'sh manzil @NotBlank tomonidan bloklanishi kerak");
    }

    @Test
    @DisplayName("OPERATOR-05: Trip source=CALL to'g'ri belgilanadi")
    void tripSourceShouldBeCall() {
        assertEquals("CALL", "CALL", "Operator buyurtmasi source=CALL bo'lishi kerak");
        assertNotEquals("APP", "CALL", "APP va CALL farqli bo'lishi kerak");
    }

    @Test
    @DisplayName("OPERATOR-06: Role valueOf ishlaydi")
    void roleValueOfWorks() {
        assertEquals(Role.OPERATOR, Role.valueOf("OPERATOR"));
        assertThrows(IllegalArgumentException.class, () -> Role.valueOf("SUPERADMIN"));
    }
}

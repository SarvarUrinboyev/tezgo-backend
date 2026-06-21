package com.taxi.backend.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BookTripRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validRequest_shouldHaveNoViolations() {
        BookTripRequest req = new BookTripRequest();
        req.setTariffId(1L);
        req.setFromLat(41.3);
        req.setFromLon(69.3);
        req.setToLat(41.4);
        req.setToLon(69.4);
        req.setFromAddress("A");
        req.setToAddress("B");
        req.setDistance(5.0);

        Set<ConstraintViolation<BookTripRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    void missingTariffId_shouldViolate() {
        BookTripRequest req = new BookTripRequest();
        req.setFromLat(41.3);
        req.setFromLon(69.3);
        req.setToLat(41.4);
        req.setToLon(69.4);
        req.setFromAddress("A");
        req.setToAddress("B");
        req.setDistance(5.0);

        Set<ConstraintViolation<BookTripRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void missingFromAddress_shouldViolate() {
        BookTripRequest req = new BookTripRequest();
        req.setTariffId(1L);
        req.setFromLat(41.3);
        req.setFromLon(69.3);
        req.setDistance(5.0);

        Set<ConstraintViolation<BookTripRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void invalidLatitude_shouldViolate() {
        BookTripRequest req = new BookTripRequest();
        req.setTariffId(1L);
        req.setFromLat(91.0); // exceeds max 90
        req.setFromLon(69.3);
        req.setFromAddress("A");
        req.setToAddress("B");
        req.setDistance(5.0);

        Set<ConstraintViolation<BookTripRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void invalidLongitude_shouldViolate() {
        BookTripRequest req = new BookTripRequest();
        req.setTariffId(1L);
        req.setFromLat(41.3);
        req.setFromLon(181.0); // exceeds max 180
        req.setFromAddress("A");
        req.setToAddress("B");
        req.setDistance(5.0);

        Set<ConstraintViolation<BookTripRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void distanceTooLarge_shouldViolate() {
        BookTripRequest req = new BookTripRequest();
        req.setTariffId(1L);
        req.setFromLat(41.3);
        req.setFromLon(69.3);
        req.setFromAddress("A");
        req.setToAddress("B");
        req.setDistance(501.0); // exceeds max 500

        Set<ConstraintViolation<BookTripRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void invalidPromoCodePattern_shouldViolate() {
        BookTripRequest req = new BookTripRequest();
        req.setTariffId(1L);
        req.setFromLat(41.3);
        req.setFromLon(69.3);
        req.setFromAddress("A");
        req.setToAddress("B");
        req.setDistance(5.0);
        req.setPromoCode("TEST@#$"); // invalid characters

        Set<ConstraintViolation<BookTripRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }
}

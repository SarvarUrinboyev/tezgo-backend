package com.taxi.backend.service;

import com.taxi.backend.enums.PhotoType;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.DriverPhoto;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverPhotoRepository;
import com.taxi.backend.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/** A6 — haydovchi hujjatlari: passport/tug'ilgan sana saqlash + hujjat rasm URL'lari (ko'rsatish). */
@ExtendWith(MockitoExtension.class)
class DriverDocumentsTest {

    @Mock private DriverRepository driverRepository;
    @Mock private DriverPhotoRepository driverPhotoRepository;

    private DriverAppService svc;
    private User user;
    private Driver driver;

    @BeforeEach
    void setup() {
        // Faqat driverRepository + driverPhotoRepository ishlatiladi; qolgani null.
        svc = new DriverAppService(driverRepository, null, null, null,
                driverPhotoRepository, null, null, null, null);
        user = new User(); user.setId(100L);
        driver = new Driver(); driver.setId(5L);
        when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
    }

    @Test
    void save_persistsPassportAndBirthDate() {
        when(driverPhotoRepository.findByDriverIdAndPhotoType(anyLong(), any())).thenReturn(Optional.empty());
        Map<String, Object> r = svc.saveDriverDocuments(user, "AA", "1234567", "1990-01-15");
        assertEquals("AA", driver.getPassportSeries());
        assertEquals("1234567", driver.getPassportNumber());
        assertEquals("1990-01-15", driver.getBirthDate());
        assertEquals("AA", r.get("passportSeries"));
        assertEquals("1990-01-15", r.get("birthDate"));
        @SuppressWarnings("unchecked")
        Map<String, Object> images = (Map<String, Object>) r.get("images");
        assertTrue(images.containsKey("idFront") && images.containsKey("idBack")
                && images.containsKey("passport") && images.containsKey("selfie"));
        assertNull(images.get("passport"));
        assertNull(images.get("selfie"));
    }

    @Test
    void get_returnsImageUrl_whenPhotoExists() {
        DriverPhoto p = new DriverPhoto();
        p.setPhotoUrl("/api/photos/view/5/passport.jpg");
        when(driverPhotoRepository.findByDriverIdAndPhotoType(5L, PhotoType.PASSPORT)).thenReturn(Optional.of(p));
        when(driverPhotoRepository.findByDriverIdAndPhotoType(5L, PhotoType.ID_FRONT)).thenReturn(Optional.empty());
        when(driverPhotoRepository.findByDriverIdAndPhotoType(5L, PhotoType.ID_BACK)).thenReturn(Optional.empty());

        Map<String, Object> r = svc.getDriverDocuments(user);
        @SuppressWarnings("unchecked")
        Map<String, Object> images = (Map<String, Object>) r.get("images");
        assertEquals("/api/photos/view/5/passport.jpg", images.get("passport"));
        assertNull(images.get("idFront"));
    }
}

package com.taxi.backend.service;

import com.taxi.backend.repository.DriverPhotoRepository;
import com.taxi.backend.repository.DriverRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Banner (reklama) rasm yuklash + ommaviy serve — path-traversal himoyasi va
 * driver-rasmlariga yeta olmasligini tekshiradi (public endpoint driver-photo
 * chegarasini buzmasligi kerak).
 */
class BannerImageTest {

    private PhotoService newService(Path root) {
        PhotoService service = new PhotoService(mock(DriverPhotoRepository.class), mock(DriverRepository.class));
        ReflectionTestUtils.setField(service, "uploadDir", root.toString());
        return service;
    }

    private static final byte[] JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    @Test
    void uploadBannerImage_validJpeg_storesUnderBannersDirAndReturnsPublicUrl(@TempDir Path tempDir) throws Exception {
        PhotoService service = newService(tempDir.resolve("uploads"));
        MockMultipartFile file = new MockMultipartFile("file", "ad.jpg", "image/jpeg", JPEG);

        Map<String, Object> result = service.uploadBannerImage(file);

        String url = (String) result.get("url");
        assertTrue(url.startsWith("/api/public/banners/banner_"), "url ommaviy /api/public/banners/ ostida bo'lishi kerak: " + url);

        Path bannersDir = service.getUploadRoot().resolve("banners");
        try (var files = Files.list(bannersDir)) {
            Path stored = files.findFirst().orElseThrow();
            assertEquals(JPEG.length, Files.size(stored));
        }
    }

    @Test
    void uploadBannerImage_wrongExtension_rejected(@TempDir Path tempDir) {
        PhotoService service = newService(tempDir.resolve("uploads"));
        MockMultipartFile file = new MockMultipartFile("file", "ad.exe", "image/jpeg", JPEG);

        assertThrows(RuntimeException.class, () -> service.uploadBannerImage(file));
    }

    @Test
    void uploadBannerImage_fakeMagicBytes_rejected(@TempDir Path tempDir) {
        PhotoService service = newService(tempDir.resolve("uploads"));
        byte[] notReallyAnImage = "this is not an image".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "ad.jpg", "image/jpeg", notReallyAnImage);

        assertThrows(RuntimeException.class, () -> service.uploadBannerImage(file));
    }

    @Test
    void servePublicBannerImage_existingFile_returns200WithBytes(@TempDir Path tempDir) throws Exception {
        PhotoService service = newService(tempDir.resolve("uploads"));
        Path bannersDir = service.getUploadRoot().resolve("banners");
        Files.createDirectories(bannersDir);
        Files.write(bannersDir.resolve("banner_abc.jpg"), JPEG);

        ResponseEntity<org.springframework.core.io.Resource> res = service.servePublicBannerImage("banner_abc.jpg");

        assertEquals(200, res.getStatusCode().value());
    }

    @Test
    void servePublicBannerImage_missingFile_returns404(@TempDir Path tempDir) {
        PhotoService service = newService(tempDir.resolve("uploads"));

        ResponseEntity<org.springframework.core.io.Resource> res = service.servePublicBannerImage("no_such_file.jpg");

        assertEquals(404, res.getStatusCode().value());
    }

    @Test
    void servePublicBannerImage_dotDotTraversal_rejectedBadRequest(@TempDir Path tempDir) {
        PhotoService service = newService(tempDir.resolve("uploads"));

        ResponseEntity<org.springframework.core.io.Resource> res =
                service.servePublicBannerImage("../drivers/5/driver_face_secret.jpg");

        assertEquals(400, res.getStatusCode().value());
    }

    /**
     * Xavfsizlik chegarasi — banner'ning ommaviy (auth'siz) serve yo'li driver rasmlariga
     * hech qanday yo'l bilan yeta olmasligi kerak. Haqiqiy driver-rasm faylini yaratib,
     * banner endpoint orqali unga yetish urinishi rad etilishini tasdiqlaydi.
     */
    @Test
    void servePublicBannerImage_cannotReachDriverPhotos_evenWithEncodedTraversal(@TempDir Path tempDir) throws Exception {
        PhotoService service = newService(tempDir.resolve("uploads"));
        Path uploadRoot = service.getUploadRoot();

        // Haqiqiy driver-rasm mavjud deb faraz qilamiz
        Path driverDir = uploadRoot.resolve("drivers").resolve("5");
        Files.createDirectories(driverDir);
        byte[] secretDriverPhoto = "REAL DRIVER FACE BYTES".getBytes();
        Files.write(driverDir.resolve("driver_face_secret.jpg"), secretDriverPhoto);

        // "/" o'zi filename tekshiruvida rad etiladi
        ResponseEntity<org.springframework.core.io.Resource> attempt1 =
                service.servePublicBannerImage("../drivers/5/driver_face_secret.jpg");
        assertEquals(400, attempt1.getStatusCode().value());

        // Backslash variantida ham xuddi shunday
        ResponseEntity<org.springframework.core.io.Resource> attempt2 =
                service.servePublicBannerImage("..\\drivers\\5\\driver_face_secret.jpg");
        assertEquals(400, attempt2.getStatusCode().value());
    }
}

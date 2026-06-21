package com.taxi.backend.service;

import com.taxi.backend.enums.PhotoType;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverPhotoRepository;
import com.taxi.backend.repository.DriverRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhotoServicePersistentStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadWritesToConfiguredAbsolutePersistentDirectory() throws Exception {
        DriverPhotoRepository photoRepository = mock(DriverPhotoRepository.class);
        DriverRepository driverRepository = mock(DriverRepository.class);
        PhotoService service = new PhotoService(photoRepository, driverRepository);

        Path configuredRoot = tempDir.resolve("uploads");
        ReflectionTestUtils.setField(service, "uploadDir", configuredRoot.toString());

        User user = new User();
        user.setId(9L);
        Driver driver = new Driver();
        driver.setId(6L);
        driver.setUser(user);

        when(driverRepository.findByUserId(9L)).thenReturn(Optional.of(driver));
        when(photoRepository.findByDriverIdAndPhotoType(6L, PhotoType.DRIVER_FACE))
                .thenReturn(Optional.empty());
        when(photoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        byte[] jpeg = new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0, 0, 0, 0, 0, 0, 0, 0
        };
        MockMultipartFile file =
                new MockMultipartFile("file", "face.jpg", "image/jpeg", jpeg);

        Map<String, Object> result = service.uploadPhoto(user, file, "DRIVER_FACE");

        Path uploadRoot = service.getUploadRoot();
        assertTrue(uploadRoot.isAbsolute());
        Path driverDir = uploadRoot.resolve("drivers").resolve("6");
        try (var files = Files.list(driverDir)) {
            Path stored = files.findFirst().orElseThrow();
            assertEquals(jpeg.length, Files.size(stored));
            assertEquals("/api/photos/view/6/" + stored.getFileName(), result.get("url"));
        }
    }
}

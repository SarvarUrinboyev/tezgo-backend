package com.taxi.backend.service;

import com.taxi.backend.enums.PhotoType;
import com.taxi.backend.enums.Role;
import com.taxi.backend.exception.ForbiddenException;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.DriverPhoto;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverPhotoRepository;
import com.taxi.backend.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    /** Ruxsat etilgan MIME turlari */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    /** Ruxsat etilgan fayl kengaytmalari */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp"
    );

    /** JPEG magic bytes: FF D8 FF */
    private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    /** PNG magic bytes: 89 50 4E 47 */
    private static final byte[] PNG_MAGIC = { (byte) 0x89, 0x50, 0x4E, 0x47 };
    /** WebP magic: 52 49 46 46 ... 57 45 42 50 (RIFF...WEBP) */
    private static final byte[] RIFF_MAGIC = { 0x52, 0x49, 0x46, 0x46 };

    /** Maksimal fayl hajmi: 10 MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private final DriverPhotoRepository photoRepository;
    private final DriverRepository driverRepository;

    public PhotoService(DriverPhotoRepository photoRepository, DriverRepository driverRepository) {
        this.photoRepository = photoRepository;
        this.driverRepository = driverRepository;
    }

    public Map<String, Object> uploadPhoto(User user, MultipartFile file, String photoTypeStr) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        PhotoType photoType;
        try {
            photoType = PhotoType.valueOf(photoTypeStr.toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("Noto'g'ri rasm turi: " + photoTypeStr);
        }

        // O'zgarmaslik — avatar (DRIVER_FACE) va selfie (SELFIE) bir martalik: birinchi muvaffaqiyatli
        // yuklashdan keyin qayta yuklash/almashtirish TAQIQLANADI (UI yashirish yetarli emas, API o'zi rad etadi).
        if ((photoType == PhotoType.DRIVER_FACE || photoType == PhotoType.SELFIE)
                && photoRepository.findByDriverIdAndPhotoType(driver.getId(), photoType).isPresent()) {
            throw new ForbiddenException("Bu rasm turini almashtirib bo'lmaydi (bir martalik, o'zgarmas)");
        }

        // === XAVFSIZLIK TEKSHIRUVLARI ===

        // 1. Fayl hajmi tekshirish
        if (file.isEmpty()) {
            throw new RuntimeException("Fayl bo'sh");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("Fayl hajmi 10MB dan oshmasligi kerak");
        }

        // 2. MIME type tekshirish
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException("Faqat JPG, PNG, WebP formatlar ruxsat etiladi");
        }

        // 3. Fayl kengaytmasi tekshirish
        String ext = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new RuntimeException("Noto'g'ri fayl kengaytmasi: " + ext);
        }

        // 4. Magic bytes tekshirish — haqiqiy rasm ekanligini tasdiqlash
        try {
            byte[] header = new byte[12];
            try (InputStream is = file.getInputStream()) {
                int read = is.read(header);
                if (read < 4) throw new RuntimeException("Fayl juda kichik");
            }
            if (!isValidImageMagic(header)) {
                throw new RuntimeException("Fayl haqiqiy rasm emas (magic bytes noto'g'ri)");
            }
        } catch (IOException e) {
            throw new RuntimeException("Faylni o'qishda xato");
        }

        // 5. Fayl nomi sanitize — path traversal himoyasi
        String safeFilename = photoType.name().toLowerCase() + "_" + UUID.randomUUID() + ext.toLowerCase();

        // === FAYL SAQLASH ===
        try {
            Path uploadRoot = getUploadRoot();
            Path dir = uploadRoot.resolve("drivers").resolve(driver.getId().toString()).normalize();
            Files.createDirectories(dir);

            Path filePath = dir.resolve(safeFilename).normalize();

            // Path traversal himoyasi: fayl uploadDir ichida ekanligini tekshirish
            if (!filePath.startsWith(uploadRoot)) {
                throw new RuntimeException("Xavfsizlik xatosi: fayl yo'li noto'g'ri");
            }

            // MultipartFile.transferTo(relativeFile) Tomcat temp katalogiga nisbatan
            // resolve qilishi mumkin. Absolute persistent path'ga stream orqali yozamiz.
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            String url = "/api/photos/view/" + driver.getId() + "/" + safeFilename;

            // Eski rasmni o'chirish (agar mavjud bo'lsa)
            photoRepository.findByDriverIdAndPhotoType(driver.getId(), photoType)
                    .ifPresent(oldPhoto -> {
                        try {
                            String oldUrl = oldPhoto.getPhotoUrl();
                            Path oldFile;
                            if (oldUrl.startsWith("/uploads/drivers/")) {
                                oldFile = uploadRoot.resolve(
                                        oldUrl.replace("/uploads/drivers/", "drivers/")).normalize();
                            } else if (oldUrl.startsWith("/api/photos/view/")) {
                                oldFile = uploadRoot.resolve("drivers").resolve(
                                        oldUrl.replace("/api/photos/view/", "")
                                                .replace("/", java.io.File.separator)).normalize();
                            } else {
                                return;
                            }
                            if (oldFile.startsWith(uploadRoot)) {
                                Files.deleteIfExists(oldFile);
                            }
                        } catch (Exception e) {
                            log.warn("Eski rasmni o'chirishda xato: {}", e.getMessage());
                        }
                    });

            // DB ga saqlash (yoki yangilash)
            DriverPhoto photo = photoRepository
                    .findByDriverIdAndPhotoType(driver.getId(), photoType)
                    .orElse(new DriverPhoto());

            photo.setDriver(driver);
            photo.setPhotoType(photoType);
            photo.setPhotoUrl(url);
            photo.setStatus(com.taxi.backend.enums.PhotoStatus.PENDING);
            photoRepository.save(photo);

            log.info("Rasm yuklandi: driverId={}, type={}, url={}", driver.getId(), photoType, url);

            // Selfie birinchi marta yuklanganda — xuddi shu rasm avatar (DRIVER_FACE) sifatida ham
            // avtomatik saqlanadi (bo'sh avatar muammosini ham hal qiladi). Bu yerda immutability
            // tekshiruvi QASDAN chetlab o'tiladi — bu tizimning o'zi qiladigan yagona yozuv.
            if (photoType == PhotoType.SELFIE) {
                copyToAvatar(driver, url, uploadRoot);
            }

            return Map.of("url", url, "type", photoType.name(), "status", "PENDING");
        } catch (IOException e) {
            throw new RuntimeException("Faylni saqlashda xato: " + e.getMessage());
        }
    }

    /** Rasmni butunlay o'chirish — DB qatori + diskdagi fayl (upload'dagi eski-fayl o'chirish yo'li bilan bir xil). */
    public Map<String, Object> deletePhoto(User user, String photoTypeStr) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        PhotoType photoType;
        try {
            photoType = PhotoType.valueOf(photoTypeStr.toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("Noto'g'ri rasm turi: " + photoTypeStr);
        }

        // O'zgarmaslik — avatar (DRIVER_FACE) va selfie (SELFIE) hech qachon o'chirilmaydi, mavjud
        // bo'lsa ham, bo'lmasa ham (turi bo'yicha shartsiz taqiq).
        if (photoType == PhotoType.DRIVER_FACE || photoType == PhotoType.SELFIE) {
            throw new ForbiddenException("Bu rasmni o'chirib bo'lmaydi (bir martalik, o'zgarmas)");
        }

        DriverPhoto photo = photoRepository.findByDriverIdAndPhotoType(driver.getId(), photoType)
                .orElseThrow(() -> new RuntimeException("Rasm topilmadi"));

        try {
            Path uploadRoot = getUploadRoot();
            String oldUrl = photo.getPhotoUrl();
            Path oldFile = null;
            if (oldUrl.startsWith("/uploads/drivers/")) {
                oldFile = uploadRoot.resolve(oldUrl.replace("/uploads/drivers/", "drivers/")).normalize();
            } else if (oldUrl.startsWith("/api/photos/view/")) {
                oldFile = uploadRoot.resolve("drivers").resolve(
                        oldUrl.replace("/api/photos/view/", "")
                                .replace("/", java.io.File.separator)).normalize();
            }
            if (oldFile != null && oldFile.startsWith(uploadRoot)) {
                Files.deleteIfExists(oldFile);
            }
        } catch (Exception e) {
            log.warn("Rasm faylini o'chirishda xato: {}", e.getMessage());
        }

        photoRepository.delete(photo);
        log.info("Rasm o'chirildi: driverId={}, type={}", driver.getId(), photoType);

        return Map.of("deleted", true, "type", photoType.name());
    }

    /**
     * ADMIN uchun — istalgan turdagi rasmni o'chiradi, DRIVER_FACE/SELFIE immutability tekshiruvini
     * QASDAN chetlab o'tadi (faqat AdminController orqali, ADMIN roli talab qilinadi — shu yerda
     * qayta tekshirilmaydi). O'chirilgan qatorni qaytaradi (chaqiruvchi driverId/type'ni log qilish
     * uchun ishlatadi — bu paytda DB'dan allaqachon o'chirilgan bo'ladi).
     */
    public DriverPhoto adminDeletePhoto(Long photoId) {
        DriverPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Rasm topilmadi"));
        try {
            Path uploadRoot = getUploadRoot();
            String oldUrl = photo.getPhotoUrl();
            Path oldFile = null;
            if (oldUrl.startsWith("/uploads/drivers/")) {
                oldFile = uploadRoot.resolve(oldUrl.replace("/uploads/drivers/", "drivers/")).normalize();
            } else if (oldUrl.startsWith("/api/photos/view/")) {
                oldFile = uploadRoot.resolve("drivers").resolve(
                        oldUrl.replace("/api/photos/view/", "")
                                .replace("/", java.io.File.separator)).normalize();
            }
            if (oldFile != null && oldFile.startsWith(uploadRoot)) {
                Files.deleteIfExists(oldFile);
            }
        } catch (Exception e) {
            log.warn("Admin o'chirishda fayl xatosi: {}", e.getMessage());
        }
        photoRepository.delete(photo);
        return photo;
    }

    /**
     * Selfie'ni avatar (DRIVER_FACE) sifatida ham saqlaydi — bir xil URL, fayl qayta nusxalanmaydi.
     * Eski avatar (agar shu feature'dan OLDIN to'g'ridan-to'g'ri yuklangan bo'lsa) bosib yoziladi —
     * chunki avatar hali "qulflanmagan" edi (selfie yo'q edi). Shundan keyin uploadPhoto'dagi
     * immutability tekshiruvi DRIVER_FACE'ni abadiy qulflaydi.
     */
    private void copyToAvatar(Driver driver, String selfieUrl, Path uploadRoot) {
        photoRepository.findByDriverIdAndPhotoType(driver.getId(), PhotoType.DRIVER_FACE)
                .ifPresent(oldAvatar -> {
                    try {
                        String oldUrl = oldAvatar.getPhotoUrl();
                        if (oldUrl.equals(selfieUrl)) return; // allaqachon shu faylga ishora qiladi
                        Path oldFile = null;
                        if (oldUrl.startsWith("/uploads/drivers/")) {
                            oldFile = uploadRoot.resolve(oldUrl.replace("/uploads/drivers/", "drivers/")).normalize();
                        } else if (oldUrl.startsWith("/api/photos/view/")) {
                            oldFile = uploadRoot.resolve("drivers").resolve(
                                    oldUrl.replace("/api/photos/view/", "")
                                            .replace("/", java.io.File.separator)).normalize();
                        }
                        if (oldFile != null && oldFile.startsWith(uploadRoot)) {
                            Files.deleteIfExists(oldFile);
                        }
                    } catch (Exception e) {
                        log.warn("Eski avatar faylini o'chirishda xato: {}", e.getMessage());
                    }
                });
        DriverPhoto avatar = photoRepository.findByDriverIdAndPhotoType(driver.getId(), PhotoType.DRIVER_FACE)
                .orElse(new DriverPhoto());
        avatar.setDriver(driver);
        avatar.setPhotoType(PhotoType.DRIVER_FACE);
        avatar.setPhotoUrl(selfieUrl);
        avatar.setStatus(com.taxi.backend.enums.PhotoStatus.PENDING);
        photoRepository.save(avatar);
        log.info("Selfie avatarga nusxalandi: driverId={}, url={}", driver.getId(), selfieUrl);
    }

    /** Protected photo serving — DRIVER faqat o'z rasmlari, ADMIN/OPERATOR hamma */
    public ResponseEntity<Resource> servePhoto(User user, Long driverId, String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        if (user.getRole() == Role.DRIVER) {
            Driver driver = driverRepository.findByUserId(user.getId())
                    .orElse(null);
            if (driver == null || !driver.getId().equals(driverId)) {
                return ResponseEntity.status(403).build();
            }
        }

        Path uploadRoot = getUploadRoot();
        Path filePath = uploadRoot.resolve("drivers").resolve(driverId.toString())
                .resolve(filename).normalize();
        if (!filePath.startsWith(uploadRoot)) {
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                    .body(new FileSystemResource(filePath));
        } catch (Exception e) {
            log.error("Rasmni serv qilishda xato: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * ADMIN uchun — banner (reklama) rasmi yuklash. Haydovchi rasmlaridan farqli o'laroq
     * alohida "banners/" papkasida saqlanadi va OMMAVIY (auth'siz) URL qaytaradi —
     * servePublicBannerImage orqali serve qilinadi, driver rasmlarining protected
     * /api/photos/view yo'liga hech qanday aloqasi yo'q.
     */
    public Map<String, Object> uploadBannerImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("Fayl bo'sh");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("Fayl hajmi 10MB dan oshmasligi kerak");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException("Faqat JPG, PNG, WebP formatlar ruxsat etiladi");
        }

        String ext = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new RuntimeException("Noto'g'ri fayl kengaytmasi: " + ext);
        }

        try {
            byte[] header = new byte[12];
            try (InputStream is = file.getInputStream()) {
                int read = is.read(header);
                if (read < 4) throw new RuntimeException("Fayl juda kichik");
            }
            if (!isValidImageMagic(header)) {
                throw new RuntimeException("Fayl haqiqiy rasm emas (magic bytes noto'g'ri)");
            }
        } catch (IOException e) {
            throw new RuntimeException("Faylni o'qishda xato");
        }

        String safeFilename = "banner_" + UUID.randomUUID() + ext.toLowerCase();

        try {
            Path uploadRoot = getUploadRoot();
            Path dir = uploadRoot.resolve("banners").normalize();
            Files.createDirectories(dir);

            Path filePath = dir.resolve(safeFilename).normalize();

            // Path traversal himoyasi: fayl banners/ papkasi ichida ekanligini tekshirish
            if (!filePath.startsWith(dir)) {
                throw new RuntimeException("Xavfsizlik xatosi: fayl yo'li noto'g'ri");
            }

            try (InputStream input = file.getInputStream()) {
                Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            String url = "/api/public/banners/" + safeFilename;
            log.info("Banner rasmi yuklandi: {}", url);
            return Map.of("url", url);
        } catch (IOException e) {
            throw new RuntimeException("Faylni saqlashda xato: " + e.getMessage());
        }
    }

    /** Ommaviy banner-rasm serve qilish — auth talab qilinmaydi (bannerlar reklama, hammaga ochiq),
     *  faqat "banners/" papkasidan — driver rasmlariga hech qanday yo'l bilan yeta olmaydi. */
    public ResponseEntity<Resource> servePublicBannerImage(String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        Path uploadRoot = getUploadRoot();
        Path dir = uploadRoot.resolve("banners").normalize();
        Path filePath = dir.resolve(filename).normalize();
        if (!filePath.startsWith(dir)) {
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(new FileSystemResource(filePath));
        } catch (Exception e) {
            log.error("Banner rasmni serv qilishda xato: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    /** Magic bytes orqali haqiqiy rasm ekanligini tekshirish */
    private boolean isValidImageMagic(byte[] header) {
        // JPEG: FF D8 FF
        if (header[0] == JPEG_MAGIC[0] && header[1] == JPEG_MAGIC[1] && header[2] == JPEG_MAGIC[2]) {
            return true;
        }
        // PNG: 89 50 4E 47
        if (header[0] == PNG_MAGIC[0] && header[1] == PNG_MAGIC[1]
                && header[2] == PNG_MAGIC[2] && header[3] == PNG_MAGIC[3]) {
            return true;
        }
        // WebP: RIFF....WEBP
        if (header[0] == RIFF_MAGIC[0] && header[1] == RIFF_MAGIC[1]
                && header[2] == RIFF_MAGIC[2] && header[3] == RIFF_MAGIC[3]
                && header.length >= 12
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
            return true;
        }
        return false;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains("."))
            return ".jpg";
        return filename.substring(filename.lastIndexOf('.'));
    }

    Path getUploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }
}

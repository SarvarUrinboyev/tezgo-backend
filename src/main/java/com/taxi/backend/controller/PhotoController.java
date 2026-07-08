package com.taxi.backend.controller;

import com.taxi.backend.model.User;
import com.taxi.backend.service.PhotoService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    /** Protected photo serving — JWT bilan autentifikatsiya talab qilinadi */
    @GetMapping("/api/photos/view/{driverId}/{filename}")
    public ResponseEntity<Resource> view(@AuthenticationPrincipal User user,
            @PathVariable Long driverId,
            @PathVariable String filename) {
        return photoService.servePhoto(user, driverId, filename);
    }

    @PostMapping({"/api/driver/photos/upload", "/api/photos/upload"})
    public ResponseEntity<?> upload(@AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "photoType", required = false) String photoType) {
        try {
            String resolvedType = photoType != null ? photoType : type;
            if (resolvedType == null || resolvedType.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "photoType parametri kerak"));
            }
            return ResponseEntity.ok(photoService.uploadPhoto(user, file, resolvedType));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping({"/api/driver/photos/{type}", "/api/photos/{type}"})
    public ResponseEntity<?> delete(@AuthenticationPrincipal User user, @PathVariable String type) {
        try {
            return ResponseEntity.ok(photoService.deletePhoto(user, type));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

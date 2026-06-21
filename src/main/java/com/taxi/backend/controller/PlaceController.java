package com.taxi.backend.controller;

import com.taxi.backend.dto.PlaceRequest;
import com.taxi.backend.service.PlaceAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PlaceController {

    private final PlaceAdminService placeAdminService;

    public PlaceController(PlaceAdminService placeAdminService) {
        this.placeAdminService = placeAdminService;
    }

    /** Public — operator va passenger qidiruvi uchun (auth kerak emas) */
    @GetMapping("/places")
    public ResponseEntity<?> getPublicPlaces() {
        return ResponseEntity.ok(placeAdminService.getPublicPlaces());
    }

    /** Admin — paginated list, optional name search */
    @GetMapping("/admin/places")
    public ResponseEntity<?> getAdminPlaces(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.min(size, 200);
        return ResponseEntity.ok(placeAdminService.getAdminPlaces(search, page, safeSize));
    }

    /** Admin — yangi joy qo'shish */
    @PostMapping("/admin/places")
    public ResponseEntity<?> create(@Valid @RequestBody PlaceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(placeAdminService.create(req));
    }

    /** Admin — joyni yangilash */
    @PutMapping("/admin/places/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody PlaceRequest req) {
        try {
            return ResponseEntity.ok(placeAdminService.update(id, req));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin — joyni o'chirish */
    @DeleteMapping("/admin/places/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            placeAdminService.delete(id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}

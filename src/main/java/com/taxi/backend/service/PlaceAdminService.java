package com.taxi.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxi.backend.dto.PlaceRequest;
import com.taxi.backend.model.Place;
import com.taxi.backend.repository.PlaceRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PlaceAdminService {

    private final PlaceRepository placeRepository;

    public PlaceAdminService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    /** Public endpoint: DB-dan qaytaradi. DB bo'sh bo'lsa places.json ga fallback */
    public List<Map<String, Object>> getPublicPlaces() {
        List<Place> dbPlaces = placeRepository.findByIsActiveTrueOrderByNameAsc();
        if (!dbPlaces.isEmpty()) {
            return dbPlaces.stream().map(this::toPublicMap).collect(Collectors.toList());
        }
        try {
            InputStream is = new ClassPathResource("places.json").getInputStream();
            return new ObjectMapper().readValue(is, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Admin: paginated, optional search by name */
    public Map<String, Object> getAdminPlaces(String search, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Place> result = (search != null && !search.isBlank())
                ? placeRepository.findByNameContainingIgnoreCase(search.trim(), pageable)
                : placeRepository.findAll(pageable);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", result.getContent().stream().map(this::toAdminMap).collect(Collectors.toList()));
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("page", page);
        response.put("size", size);
        return response;
    }

    @Transactional
    public Map<String, Object> create(PlaceRequest req) {
        Place p = new Place();
        p.setName(req.getName().trim());
        p.setLat(req.getLat());
        p.setLon(req.getLon());
        p.setAliases(req.getAliases() != null ? req.getAliases().trim() : null);
        return toAdminMap(placeRepository.save(p));
    }

    @Transactional
    public Map<String, Object> update(Long id, PlaceRequest req) {
        Place p = placeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Joy topilmadi: " + id));
        p.setName(req.getName().trim());
        p.setLat(req.getLat());
        p.setLon(req.getLon());
        p.setAliases(req.getAliases() != null ? req.getAliases().trim() : null);
        return toAdminMap(placeRepository.save(p));
    }

    @Transactional
    public void delete(Long id) {
        if (!placeRepository.existsById(id)) {
            throw new RuntimeException("Joy topilmadi: " + id);
        }
        placeRepository.deleteById(id);
    }

    private Map<String, Object> toPublicMap(Place p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("lat", p.getLat());
        m.put("lon", p.getLon());
        List<String> alt = (p.getAliases() != null && !p.getAliases().isBlank())
                ? Arrays.stream(p.getAliases().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .collect(Collectors.toList())
                : List.of();
        m.put("alt", alt);
        return m;
    }

    private Map<String, Object> toAdminMap(Place p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("lat", p.getLat());
        m.put("lon", p.getLon());
        m.put("aliases", p.getAliases() != null ? p.getAliases() : "");
        m.put("isActive", p.isActive());
        m.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
        return m;
    }
}

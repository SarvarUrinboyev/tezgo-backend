package com.taxi.backend.service;

import com.taxi.backend.dto.OperatorTripRequest;
import com.taxi.backend.exception.ConflictException;
import com.taxi.backend.model.OperatorTripIdempotency;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.OperatorTripIdempotencyRepository;
import com.taxi.backend.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns the HTTP idempotency boundary. The idempotency row and trip creation are
 * committed atomically; replay never calls the dispatching create path again.
 */
@Service
public class OperatorTripCreateIdempotencyService {

    private final OperatorTripIdempotencyRepository idempotencyRepository;
    private final OperatorService operatorService;
    private final TripRepository tripRepository;
    private final ApiRateLimitService rateLimitService;

    public OperatorTripCreateIdempotencyService(OperatorTripIdempotencyRepository idempotencyRepository,
                                                OperatorService operatorService,
                                                TripRepository tripRepository,
                                                ApiRateLimitService rateLimitService) {
        this.idempotencyRepository = idempotencyRepository;
        this.operatorService = operatorService;
        this.tripRepository = tripRepository;
        this.rateLimitService = rateLimitService;
    }

    @Transactional
    public Map<String, Object> createTrip(User operator, String idempotencyKey, OperatorTripRequest request) {
        String normalizedKey = requireCanonicalUuid(idempotencyKey);
        String requestHash = requestHash(request);
        int claimed = idempotencyRepository.claim(operator.getId(), normalizedKey, requestHash);

        OperatorTripIdempotency record = idempotencyRepository
                .findWithTripByOperatorIdAndIdempotencyKey(operator.getId(), normalizedKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency claim topilmadi"));

        if (claimed == 0) {
            if (!MessageDigest.isEqual(record.getRequestHash().getBytes(StandardCharsets.US_ASCII),
                    requestHash.getBytes(StandardCharsets.US_ASCII))) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED");
            }
            if (!"COMPLETED".equals(record.getState()) || record.getTrip() == null) {
                throw new ConflictException("IDEMPOTENCY_REQUEST_IN_PROGRESS");
            }
            return replayResponse(record);
        }

        // Replays must not consume a new create quota. A rejected new request rolls
        // back its claim with the transaction, so a later valid attempt can claim it.
        rateLimitService.checkLimit("operator:" + operator.getId(), 20, 60);
        Map<String, Object> response = new HashMap<>(operatorService.createTrip(operator, request));
        Long tripId = ((Number) response.get("tripId")).longValue();
        Trip trip = tripRepository.getReferenceById(tripId);
        record.setTrip(trip);
        record.setResponseStatus(String.valueOf(response.get("status")));
        record.setResponseSource(String.valueOf(response.get("source")));
        record.setState("COMPLETED");
        idempotencyRepository.save(record);
        response.put("idempotencyReplay", false);
        return response;
    }

    private Map<String, Object> replayResponse(OperatorTripIdempotency record) {
        Trip trip = record.getTrip();
        Map<String, Object> response = new HashMap<>();
        response.put("tripId", trip.getId());
        response.put("status", record.getResponseStatus() != null
                ? record.getResponseStatus() : trip.getStatus().name());
        response.put("source", record.getResponseSource() != null
                ? record.getResponseSource() : trip.getSource());
        response.put("idempotencyReplay", true);
        return response;
    }

    private String requireCanonicalUuid(String idempotencyKey) {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("Idempotency-Key kerak");
        }
        try {
            UUID parsed = UUID.fromString(idempotencyKey);
            if (!parsed.toString().equals(idempotencyKey)) {
                throw new IllegalArgumentException("Idempotency-Key UUID formatida bo'lishi kerak");
            }
            return parsed.toString();
        } catch (IllegalArgumentException exception) {
            if ("Idempotency-Key UUID formatida bo'lishi kerak".equals(exception.getMessage())) {
                throw exception;
            }
            throw new IllegalArgumentException("Idempotency-Key UUID formatida bo'lishi kerak");
        }
    }

    /** Canonical order matters; raw PII is never persisted with the key. */
    private String requestHash(OperatorTripRequest request) {
        String canonical = String.join("|",
                value(request.getPassengerPhone()), value(request.getPickupAddress()),
                value(request.getDestinationAddress()), value(request.getTariffId()), value(request.getMode()),
                canonicalServices(request.getSelectedServices()), value(request.getFromLat()), value(request.getFromLon()),
                value(request.getToLat()), value(request.getToLon()));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : hash) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 mavjud emas", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "<null>" : value.toString().trim();
    }

    private String canonicalServices(List<String> services) {
        if (services == null) {
            return "<null>";
        }
        return services.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining(","));
    }
}

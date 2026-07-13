package com.taxi.backend.service;

import com.taxi.backend.dto.OperatorTripRequest;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.exception.ConflictException;
import com.taxi.backend.model.OperatorTripIdempotency;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.OperatorTripIdempotencyRepository;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperatorTripCreateIdempotencyServiceTest {

    private static final String KEY = "11111111-1111-4111-8111-111111111111";

    @Mock private OperatorTripIdempotencyRepository idempotencyRepository;
    @Mock private OperatorService operatorService;
    @Mock private TripRepository tripRepository;
    @Mock private ApiRateLimitService rateLimitService;

    private OperatorTripCreateIdempotencyService service;
    private User operator;

    @BeforeEach
    void setUp() {
        service = new OperatorTripCreateIdempotencyService(idempotencyRepository, operatorService, tripRepository,
                rateLimitService);
        operator = new User();
        operator.setId(7L);
    }

    @Test
    void firstClaim_createsOneTripAndMarksDurableRecordCompleted() {
        OperatorTripRequest request = request("Pickup A");
        OperatorTripIdempotency record = record(hash(request), "PROCESSING", null);
        Trip persistedTrip = new Trip();
        persistedTrip.setId(91L);
        when(idempotencyRepository.claim(operator.getId(), KEY, hash(request))).thenReturn(1);
        when(idempotencyRepository.findWithTripByOperatorIdAndIdempotencyKey(operator.getId(), KEY))
                .thenReturn(Optional.of(record));
        when(operatorService.createTrip(operator, request)).thenReturn(
                Map.of("tripId", 91L, "status", "SEARCHING", "source", "CALL"));
        when(tripRepository.getReferenceById(91L)).thenReturn(persistedTrip);

        Map<String, Object> response = service.createTrip(operator, KEY, request);

        assertFalse((Boolean) response.get("idempotencyReplay"));
        assertEquals("COMPLETED", record.getState());
        assertEquals(91L, record.getTrip().getId());
        assertEquals("SEARCHING", record.getResponseStatus());
        assertEquals("CALL", record.getResponseSource());
        verify(operatorService).createTrip(operator, request);
        verify(rateLimitService).checkLimit("operator:7", 20, 60);
        verify(idempotencyRepository).save(record);
    }

    @Test
    void sameKeyAndSamePayload_returnsExistingTripWithoutSecondCreateOrDispatchPath() {
        OperatorTripRequest request = request("Pickup A");
        Trip existingTrip = new Trip();
        existingTrip.setId(92L);
        existingTrip.setStatus(TripStatus.ACCEPTED);
        existingTrip.setSource("APP");
        OperatorTripIdempotency record = record(hash(request), "COMPLETED", existingTrip);
        record.setResponseStatus("SEARCHING");
        record.setResponseSource("CALL");
        when(idempotencyRepository.claim(operator.getId(), KEY, hash(request))).thenReturn(0);
        when(idempotencyRepository.findWithTripByOperatorIdAndIdempotencyKey(operator.getId(), KEY))
                .thenReturn(Optional.of(record));

        Map<String, Object> response = service.createTrip(operator, KEY, request);

        assertTrue((Boolean) response.get("idempotencyReplay"));
        assertEquals(92L, response.get("tripId"));
        assertEquals("SEARCHING", response.get("status"));
        assertEquals("CALL", response.get("source"));
        verify(operatorService, never()).createTrip(operator, request);
        verify(tripRepository, never()).getReferenceById(92L);
        verify(rateLimitService, never()).checkLimit("operator:7", 20, 60);
    }

    @Test
    void sameKeyWithChangedPayload_returnsConflictBeforeTripCreate() {
        OperatorTripRequest original = request("Pickup A");
        OperatorTripRequest changed = request("Pickup B");
        when(idempotencyRepository.claim(operator.getId(), KEY, hash(changed))).thenReturn(0);
        when(idempotencyRepository.findWithTripByOperatorIdAndIdempotencyKey(operator.getId(), KEY))
                .thenReturn(Optional.of(record(hash(original), "COMPLETED", new Trip())));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> service.createTrip(operator, KEY, changed));

        assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getMessage());
        verify(operatorService, never()).createTrip(operator, changed);
    }

    @Test
    void incompleteSameKeyIsNotRetriedThroughTheCreateOrDispatchPath() {
        OperatorTripRequest request = request("Pickup A");
        when(idempotencyRepository.claim(operator.getId(), KEY, hash(request))).thenReturn(0);
        when(idempotencyRepository.findWithTripByOperatorIdAndIdempotencyKey(operator.getId(), KEY))
                .thenReturn(Optional.of(record(hash(request), "PROCESSING", null)));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> service.createTrip(operator, KEY, request));

        assertEquals("IDEMPOTENCY_REQUEST_IN_PROGRESS", exception.getMessage());
        verify(operatorService, never()).createTrip(operator, request);
        verify(rateLimitService, never()).checkLimit("operator:7", 20, 60);
    }

    @Test
    void missingOrNonCanonicalKeyIsRejectedBeforeAnyDatabaseMutation() {
        OperatorTripRequest request = request("Pickup A");

        assertThrows(IllegalArgumentException.class, () -> service.createTrip(operator, null, request));
        assertThrows(IllegalArgumentException.class,
                () -> service.createTrip(operator, "11111111-1111-4111-8111-111111111111 ", request));
        verify(idempotencyRepository, never()).claim(operator.getId(), KEY, hash(request));
    }

    private OperatorTripIdempotency record(String requestHash, String state, Trip trip) {
        OperatorTripIdempotency record = new OperatorTripIdempotency();
        record.setRequestHash(requestHash);
        record.setState(state);
        record.setTrip(trip);
        return record;
    }

    private OperatorTripRequest request(String pickup) {
        OperatorTripRequest request = new OperatorTripRequest();
        request.setPassengerPhone("+998900000001");
        request.setPickupAddress(pickup);
        request.setDestinationAddress("Destination");
        request.setTariffId(1L);
        request.setMode("FIXED");
        request.setSelectedServices(List.of("AC"));
        request.setFromLat(41.30);
        request.setFromLon(69.68);
        request.setToLat(41.31);
        request.setToLon(69.69);
        return request;
    }

    private String hash(OperatorTripRequest request) {
        String canonical = String.join("|", request.getPassengerPhone(), request.getPickupAddress(),
                request.getDestinationAddress(), request.getTariffId().toString(), request.getMode(),
                String.join(",", request.getSelectedServices().stream().map(String::trim).map(String::toUpperCase).sorted().toList()),
                request.getFromLat().toString(), request.getFromLon().toString(),
                request.getToLat().toString(), request.getToLon().toString());
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}

package com.taxi.backend.repository;

import com.taxi.backend.model.OperatorTripIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OperatorTripIdempotencyRepository extends JpaRepository<OperatorTripIdempotency, Long> {

    /** Atomic PostgreSQL claim. A concurrent duplicate waits for the winning transaction. */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO operator_trip_idempotencies "
            + "(operator_id, idempotency_key, request_hash, state, created_at, updated_at) "
            + "VALUES (:operatorId, :idempotencyKey, :requestHash, 'PROCESSING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (operator_id, idempotency_key) DO NOTHING", nativeQuery = true)
    int claim(@Param("operatorId") Long operatorId,
              @Param("idempotencyKey") String idempotencyKey,
              @Param("requestHash") String requestHash);

    @Query("SELECT i FROM OperatorTripIdempotency i "
            + "LEFT JOIN FETCH i.trip WHERE i.operator.id = :operatorId "
            + "AND i.idempotencyKey = :idempotencyKey")
    Optional<OperatorTripIdempotency> findWithTripByOperatorIdAndIdempotencyKey(
            @Param("operatorId") Long operatorId,
            @Param("idempotencyKey") String idempotencyKey);
}

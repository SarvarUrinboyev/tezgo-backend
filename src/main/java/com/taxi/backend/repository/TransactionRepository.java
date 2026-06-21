package com.taxi.backend.repository;

import com.taxi.backend.enums.TransactionType;
import com.taxi.backend.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Page<Transaction> findByDriverIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);

    List<Transaction> findByDriverId(Long driverId);

    Page<Transaction> findByType(TransactionType type, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.driver.id = :driverId AND t.createdAt >= :from")
    Long sumIncomeAfter(@org.springframework.data.repository.query.Param("driverId") Long driverId,
                        @org.springframework.data.repository.query.Param("from") LocalDateTime from);

    /** Haydovchi tranzaksiyalarini turi bo'yicha olish (paginated) */
    Page<Transaction> findByDriverIdAndTypeOrderByCreatedAtDesc(Long driverId, TransactionType type, Pageable pageable);

    // ── Dashboard kengaytirilgan statistika ────────────────────────────────

    /** COMMISSION + TAXOMETER_COMMISSION: COMMISSION musbat, TAXOMETER_COMMISSION manfiy — ABS ishlatiladi */
    @Query("SELECT COALESCE(SUM(ABS(t.amount)), 0) FROM Transaction t WHERE t.type IN :types AND t.createdAt >= :from")
    Long sumAbsAmountByTypesAfter(@org.springframework.data.repository.query.Param("types") List<TransactionType> types,
                                   @org.springframework.data.repository.query.Param("from") LocalDateTime from);

    /** Admin qo'l to'ldirish (paymentMethod IS NOT NULL = admin tomonidan) */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'TOPUP' AND t.paymentMethod IS NOT NULL AND t.createdAt >= :from")
    Long sumAdminTopupAfter(@org.springframework.data.repository.query.Param("from") LocalDateTime from);

    @Query("SELECT COUNT(DISTINCT t.driver.id) FROM Transaction t WHERE t.type = 'TOPUP' AND t.paymentMethod IS NOT NULL AND t.createdAt >= :from")
    Long countDistinctDriversAdminTopupAfter(@org.springframework.data.repository.query.Param("from") LocalDateTime from);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'TOPUP' AND t.paymentMethod = :method AND t.createdAt >= :from")
    Long sumAdminTopupByMethodAfter(@org.springframework.data.repository.query.Param("method") String method,
                                     @org.springframework.data.repository.query.Param("from") LocalDateTime from);
}

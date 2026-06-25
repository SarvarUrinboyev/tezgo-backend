package com.taxi.backend.repository;

import com.taxi.backend.model.ClickShopTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Click ADVANCED SHOP idempotency ledger.
 *
 * Same pattern as {@link ClickTransactionRepository} but on a separate table
 * ({@code click_shop_transactions}, V42). Pulni kreditlash bu yerda EMAS — bu yer faqat
 * "shu click_paydoc_id ishlanganmi?" degan bardoshli (Redis'dan mustaqil) javob beradi.
 *
 * Idempotentlikning yuragi — {@link #markConfirmedIfNotAlready}: shartli UPDATE
 * (WHERE status &lt;&gt; 'CONFIRMED'). Postgres row-level lock tufayli bir vaqtda
 * kelgan ikki COMPLETE'dan FAQAT bittasi rowcount=1 oladi (kreditlaydi).
 */
public interface ClickShopTransactionRepository extends JpaRepository<ClickShopTransaction, Long> {

    Optional<ClickShopTransaction> findByClickPaydocId(String clickPaydocId);

    /**
     * Ledgerga PREPARED satr qo'shadi; agar click_paydoc_id allaqachon bo'lsa — NO-OP
     * (Postgres ON CONFLICT DO NOTHING). Hech qachon exception tashlamaydi.
     * Qaytaradi: 1 = qo'shildi, 0 = bor edi.
     */
    @Modifying
    @Query(nativeQuery = true, value =
            "INSERT INTO click_shop_transactions " +
            "(click_paydoc_id, attempt_trans_id, driver_id, amount, action, status, merchant_prepare_id, created_at) " +
            "VALUES (:clickPaydocId, :attemptTransId, :driverId, :amount, :action, 'PREPARED', :merchantPrepareId, now()) " +
            "ON CONFLICT (click_paydoc_id) DO NOTHING")
    int insertIfAbsent(@Param("clickPaydocId") String clickPaydocId,
                       @Param("attemptTransId") String attemptTransId,
                       @Param("driverId") Long driverId,
                       @Param("amount") long amount,
                       @Param("action") int action,
                       @Param("merchantPrepareId") String merchantPrepareId);

    /**
     * ATOMIK CLAIM: statusni CONFIRMED ga o'tkazadi, faqat agar hali CONFIRMED bo'lmagan bo'lsa.
     * Qaytaradi: 1 = shu chaqiruvchi g'olib (kreditlashi SHART), 0 = allaqachon CONFIRMED
     * (idempotent hit — kreditlamaslik kerak). Double-credit'ga qarshi bardoshli kafolat.
     */
    @Modifying
    @Query(nativeQuery = true, value =
            "UPDATE click_shop_transactions " +
            "SET status='CONFIRMED', merchant_confirm_id=:confirmId, action=2, error=0, completed_at=now() " +
            "WHERE click_paydoc_id=:clickPaydocId AND status <> 'CONFIRMED'")
    int markConfirmedIfNotAlready(@Param("clickPaydocId") String clickPaydocId,
                                  @Param("confirmId") String confirmId);

    /** Bekor qilingan/muvaffaqiyatsiz Click COMPLETE — CONFIRMED bo'lmagan satrni CANCELLED qiladi. */
    @Modifying
    @Query(nativeQuery = true, value =
            "UPDATE click_shop_transactions SET status='CANCELLED', error=:error, completed_at=now() " +
            "WHERE click_paydoc_id=:clickPaydocId AND status <> 'CONFIRMED'")
    int markCancelled(@Param("clickPaydocId") String clickPaydocId, @Param("error") int error);
}

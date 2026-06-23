package com.taxi.backend.repository;

import com.taxi.backend.model.ClickTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Click idempotency ledgeri. Pulni kreditlash bu yerda EMAS — bu yer faqat
 * "shu click_trans_id ishlanganmi?" degan bardoshli (Redis'dan mustaqil) javob beradi.
 *
 * Idempotentlikning yuragi — {@link #markConfirmedIfNotAlready}: shartli UPDATE
 * (WHERE status &lt;&gt; 'CONFIRMED'). Postgres satr-darajali locklash tufayli bir vaqtda
 * kelgan ikki COMPLETE'dan FAQAT bittasi rowcount=1 oladi (kreditlaydi), ikkinchisi 0
 * oladi (idempotent — kreditlamaydi). UNIQUE(click_trans_id) esa qayta-yetkazishni to'sadi.
 */
public interface ClickTransactionRepository extends JpaRepository<ClickTransaction, Long> {

    Optional<ClickTransaction> findByClickTransId(String clickTransId);

    /**
     * Ledgerga PREPARED satr qo'shadi; agar click_trans_id allaqachon bo'lsa — NO-OP
     * (Postgres ON CONFLICT DO NOTHING). Hech qachon exception tashlamaydi, shuning uchun
     * chaqiruvchi tranzaksiyasini "rollback-only" ga aylantirmaydi. Qaytaradi: 1 = qo'shildi, 0 = bor edi.
     */
    @Modifying
    @Query(nativeQuery = true, value =
            "INSERT INTO click_transactions " +
            "(click_trans_id, merchant_trans_id, driver_id, amount, action, status, merchant_prepare_id, created_at) " +
            "VALUES (:clickTransId, :merchantTransId, :driverId, :amount, :action, 'PREPARED', :merchantPrepareId, now()) " +
            "ON CONFLICT (click_trans_id) DO NOTHING")
    int insertIfAbsent(@Param("clickTransId") String clickTransId,
                       @Param("merchantTransId") String merchantTransId,
                       @Param("driverId") Long driverId,
                       @Param("amount") long amount,
                       @Param("action") int action,
                       @Param("merchantPrepareId") String merchantPrepareId);

    /**
     * ATOMIK CLAIM: statusni CONFIRMED ga o'tkazadi, faqat agar hali CONFIRMED bo'lmagan bo'lsa.
     * Qaytaradi: 1 = shu chaqiruvchi g'olib (kreditlashi SHART), 0 = allaqachon CONFIRMED
     * (idempotent hit — kreditlamaslik kerak). Bu — double-credit'ga qarshi bardoshli kafolat.
     */
    @Modifying
    @Query(nativeQuery = true, value =
            "UPDATE click_transactions " +
            "SET status='CONFIRMED', merchant_confirm_id=:confirmId, action=1, error=0, completed_at=now() " +
            "WHERE click_trans_id=:clickTransId AND status <> 'CONFIRMED'")
    int markConfirmedIfNotAlready(@Param("clickTransId") String clickTransId,
                                  @Param("confirmId") String confirmId);

    /** Bekor qilingan/muvaffaqiyatsiz Click COMPLETE (error &lt; 0) — CONFIRMED bo'lmagan satrni CANCELLED qiladi. */
    @Modifying
    @Query(nativeQuery = true, value =
            "UPDATE click_transactions SET status='CANCELLED', error=:error, completed_at=now() " +
            "WHERE click_trans_id=:clickTransId AND status <> 'CONFIRMED'")
    int markCancelled(@Param("clickTransId") String clickTransId, @Param("error") int error);
}

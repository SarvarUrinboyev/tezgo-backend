package com.taxi.backend.repository;

import com.taxi.backend.model.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    /** Bitta yo'lovchi thread'i — vaqt tartibida (eskidan yangiga). */
    List<SupportMessage> findByUserIdOrderByCreatedAtAsc(Long userId);

    /** Yo'lovchi uchun o'qilmagan (operator yozgan) xabarlar soni — badge. */
    long countByUserIdAndReadByPassengerFalseAndSenderRole(Long userId, String senderRole);

    /** Operator uchun global o'qilmagan (yo'lovchi yozgan) xabarlar soni — inbox badge. */
    long countByReadByOperatorFalseAndSenderRole(String senderRole);

    /** Har thread'dagi OXIRGI xabar (admin suhbatlar ro'yxati uchun). id monoton — MAX(id)=eng yangi. */
    @Query("SELECT m FROM SupportMessage m WHERE m.id IN " +
           "(SELECT MAX(m2.id) FROM SupportMessage m2 GROUP BY m2.user.id) " +
           "ORDER BY m.createdAt DESC")
    List<SupportMessage> findLatestPerThread();

    /** Har thread bo'yicha o'qilmagan (yo'lovchi) xabarlar soni: [userId, count]. */
    @Query("SELECT m.user.id, COUNT(m) FROM SupportMessage m " +
           "WHERE m.readByOperator = false AND m.senderRole = 'PASSENGER' " +
           "GROUP BY m.user.id")
    List<Object[]> findUnreadCountsPerThread();

    /** Operator thread'ni ochganda — yo'lovchi xabarlarini o'qilgan deb belgilash. */
    @Modifying
    @Query("UPDATE SupportMessage m SET m.readByOperator = true " +
           "WHERE m.user.id = :userId AND m.senderRole = 'PASSENGER' AND m.readByOperator = false")
    int markPassengerMessagesReadByOperator(@Param("userId") Long userId);

    /** Yo'lovchi thread'ni ochganda — operator xabarlarini o'qilgan deb belgilash. */
    @Modifying
    @Query("UPDATE SupportMessage m SET m.readByPassenger = true " +
           "WHERE m.user.id = :userId AND m.senderRole = 'OPERATOR' AND m.readByPassenger = false")
    int markOperatorMessagesReadByPassenger(@Param("userId") Long userId);
}

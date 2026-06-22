package com.taxi.backend.repository;

import com.taxi.backend.model.ChannelMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, Long> {

    /** Bitta haydovchining bitta kanali — vaqt tartibida (eskidan yangiga). */
    List<ChannelMessage> findByDriverIdAndChannelOrderByCreatedAtAsc(Long driverId, String channel);

    /** Bitta kanal bo'yicha o'qilmaganlar soni. */
    long countByDriverIdAndChannelAndReadAtIsNull(Long driverId, String channel);

    /** Barcha kanallar bo'yicha o'qilmaganlar: [channel, count] — badge'lar uchun. */
    @Query("SELECT m.channel, COUNT(m) FROM ChannelMessage m " +
           "WHERE m.driverId = :driverId AND m.readAt IS NULL GROUP BY m.channel")
    List<Object[]> unreadCountsByChannel(@Param("driverId") Long driverId);

    /** Kanal ochilganda — o'qilmaganlarni o'qilgan deb belgilash. */
    @Modifying
    @Query("UPDATE ChannelMessage m SET m.readAt = :now " +
           "WHERE m.driverId = :driverId AND m.channel = :channel AND m.readAt IS NULL")
    int markChannelRead(@Param("driverId") Long driverId, @Param("channel") String channel,
                        @Param("now") LocalDateTime now);
}

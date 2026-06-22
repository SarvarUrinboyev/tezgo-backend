package com.taxi.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Per-haydovchi, per-kanal xabar (admin -> driver, faqat o'qish). V39.
 *
 * Admin kanalga yuborganda har targetlangan haydovchiga bitta satr ochiladi (fan-out) —
 * shuning uchun o'qilganlik (read_at) har haydovchi uchun mustaqil kuzatiladi (badge).
 * TEXNIK_YORDAM bu yerda EMAS (u support_messages, 2 tomonlama).
 */
@Entity
@Table(name = "channel_messages")
public class ChannelMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    // PRO_YANGILIKLARI | BONUSLAR | QOLLAB_QUVVATLASH | OGOHLANTIRISHLAR | HISOB_BALANS
    @Column(nullable = false, length = 32)
    private String channel;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public ChannelMessage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Long getSentBy() { return sentBy; }
    public void setSentBy(Long sentBy) { this.sentBy = sentBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}

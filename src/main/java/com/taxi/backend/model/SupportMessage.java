package com.taxi.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Qo'llab-quvvatlash (support) chat xabari — yo'lovchi ↔ operator/admin.
 *
 * Trip chatidan FARQI: bu DOIMIY saqlanadi (Postgres), 24 soatlik Redis emas.
 * Suhbat (thread) user_id bo'yicha guruhlanadi: har bir yo'lovchi = bitta thread.
 * Operator javob yozsa ham xabar shu yo'lovchining user_id si ostida saqlanadi
 * (user_id = thread egasi, sender_id = aslida yozgan akkaunt).
 */
@Entity
@Table(name = "support_messages")
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Thread egasi = yo'lovchi (operator yozsa ham shu user_id ostida saqlanadi).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // PASSENGER | OPERATOR
    @Column(name = "sender_role", nullable = false, length = 20)
    private String senderRole;

    // PASSENGER | DRIVER — thread egasining turi (V38). Mavjud satrlar default 'PASSENGER'.
    // Driver va passenger user_id lari boshqa-boshqa, lekin operator inboxda turini ajratish kerak.
    @Column(name = "thread_type", nullable = false, length = 10)
    private String threadType = "PASSENGER";

    // Aslida xabarni yuborgan account (operator bo'lsa — operatorning user.id si). Audit uchun.
    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "read_by_passenger", nullable = false)
    private boolean readByPassenger = false;

    @Column(name = "read_by_operator", nullable = false)
    private boolean readByOperator = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public SupportMessage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }
    public String getThreadType() { return threadType; }
    public void setThreadType(String threadType) { this.threadType = threadType; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public boolean isReadByPassenger() { return readByPassenger; }
    public void setReadByPassenger(boolean v) { this.readByPassenger = v; }
    public boolean isReadByOperator() { return readByOperator; }
    public void setReadByOperator(boolean v) { this.readByOperator = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

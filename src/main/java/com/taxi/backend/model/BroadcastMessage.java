package com.taxi.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "broadcast_messages")
public class BroadcastMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // ALL | ACTIVE | OFFLINE | SPECIFIC
    @Column(length = 20)
    private String target = "ALL";

    // Specific haydovchilar ID'lari
    @ElementCollection
    @CollectionTable(name = "broadcast_targets", joinColumns = @JoinColumn(name = "message_id"))
    @Column(name = "driver_id")
    private List<Long> targetDriverIds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by")
    private User sentBy;

    @Column(name = "sent_at")
    private LocalDateTime sentAt = LocalDateTime.now();

    public BroadcastMessage() {
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public List<Long> getTargetDriverIds() {
        return targetDriverIds;
    }

    public void setTargetDriverIds(List<Long> targetDriverIds) {
        this.targetDriverIds = targetDriverIds;
    }

    public User getSentBy() {
        return sentBy;
    }

    public void setSentBy(User sentBy) {
        this.sentBy = sentBy;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}

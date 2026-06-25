package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin broadcast xabar */
public class BroadcastRequest {
    @Size(max = 200, message = "Sarlavha 200 belgidan oshmasin")
    private String title;

    @NotBlank(message = "Xabar matni bo'sh bo'lishi mumkin emas")
    @Size(max = 1000, message = "Xabar 1000 belgidan oshmasin")
    private String content;

    @Size(max = 20)
    private String target = "ALL"; // ALL, ACTIVE, OFFLINE, DRIVER

    /** Band 7 — agar target=DRIVER bo'lsa, MAJBURIY: aniq Driver primary key. Admin paneli driver_code (TZ-XXXX) ni shu id ga aylantiradi. */
    private Long driverId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
}

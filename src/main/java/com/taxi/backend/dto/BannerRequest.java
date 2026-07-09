package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class BannerRequest {
    @NotBlank(message = "Sarlavha bo'sh bo'lmasligi kerak")
    @Size(max = 200, message = "Sarlavha juda uzun")
    private String title;

    @Size(max = 300, message = "Kichik sarlavha juda uzun")
    private String subtitle;

    @NotBlank(message = "Fon rangi ko'rsatilishi shart")
    @Size(max = 9, message = "Rang formati noto'g'ri")
    private String bgColor;

    private String imageUrl;
    private String linkUrl;
    private int sort = 0;
    private boolean active = true;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getBgColor() { return bgColor; }
    public void setBgColor(String bgColor) { this.bgColor = bgColor; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
    public int getSort() { return sort; }
    public void setSort(int sort) { this.sort = sort; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
}

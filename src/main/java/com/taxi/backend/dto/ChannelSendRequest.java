package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin -> kanal broadcast. channel URL path'da; bu yerda matn + audience. */
public class ChannelSendRequest {

    @Size(max = 200, message = "Sarlavha 200 belgidan oshmasin")
    private String title;

    @NotBlank(message = "Xabar matni bo'sh bo'lishi mumkin emas")
    @Size(max = 1000, message = "Xabar 1000 belgidan oshmasin")
    private String body;

    @Size(max = 20)
    private String target = "ALL"; // ALL | ACTIVE | OFFLINE

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
}

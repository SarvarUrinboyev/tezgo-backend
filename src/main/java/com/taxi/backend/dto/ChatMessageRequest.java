package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Chat xabar yuborish */
public class ChatMessageRequest {
    @NotBlank(message = "Xabar bo'sh bo'lishi mumkin emas")
    @Size(max = 500, message = "Xabar 500 belgidan oshmasin")
    private String text;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}

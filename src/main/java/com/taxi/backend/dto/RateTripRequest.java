package com.taxi.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RateTripRequest {
    @NotNull(message = "Baho kerak")
    @Min(value = 1, message = "Minimal baho: 1")
    @Max(value = 5, message = "Maksimal baho: 5")
    private Integer rating;

    private String comment;

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

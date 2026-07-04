package com.urlShortner.v1.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShortenUrlRequest {
    private String originalUrl; // The original URL to be shortened
    private LocalDateTime expiresAt; // The expiration date of the shortened URL
    private LocalDateTime createdAt; // The creation date of the shortened URL
}

package com.urlShortner.v1.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import lombok.*;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortenUrlRequest {
    private String originalUrl; // original URL to be shortened
    private LocalDateTime expiresAt; // expiration date of the shortened URL
    private LocalDateTime createdAt; // creation date of the shortened URL
}

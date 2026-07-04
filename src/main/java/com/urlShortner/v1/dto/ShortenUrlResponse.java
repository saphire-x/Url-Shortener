package com.urlShortner.v1.dto;

import java.time.LocalDateTime;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShortenUrlResponse {

    private String shortUrl; // The shortened URL
    private String originalUrl; // The original URL
    private LocalDateTime createdAt; // The creation date of the shortened URL
    private LocalDateTime expiresAt; // The expiration date of the shortened URL
}

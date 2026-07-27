package com.urlShortner.v1.dto;

import java.time.LocalDateTime;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortenUrlResponse {

    private String shortUrl; // shortened URL
    private String originalUrl; //original URL
    private LocalDateTime createdAt; // creation date of the shortened URL
    private LocalDateTime expiresAt; // expiration date of the shortened URL
}

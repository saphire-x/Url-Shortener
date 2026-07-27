package com.urlShortner.v1.controller;

import com.urlShortner.v1.dto.ShortenUrlResponse;
import com.urlShortner.v1.entity.ShortUrl;
import com.urlShortner.v1.service.UrlShortenerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api")
public class ShortenUrlRestController {

    private final UrlShortenerService urlShortenerService;

    public ShortenUrlRestController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenUrlResponse> shorten(
            @RequestParam String originalUrl) {

        ShortUrl shortUrl =
                urlShortenerService.createShortUrl(originalUrl);

        String fullShortUrl =
                ServletUriComponentsBuilder
                        .fromCurrentContextPath()
                        .path("/")
                        .path(shortUrl.getShortUrl())
                        .build()
                        .toUriString();

        return ResponseEntity.ok(
                new ShortenUrlResponse(
                        fullShortUrl,
                        shortUrl.getOriginalUrl(),
                        shortUrl.getCreatedAt(),
                        shortUrl.getExpiresAt()
                )
        );
    }
}
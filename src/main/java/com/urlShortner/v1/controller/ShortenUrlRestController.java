package com.urlShortner.v1.controller;

import com.urlShortner.v1.dto.ShortenUrlResponse;
import com.urlShortner.v1.entity.ShortUrl;
import com.urlShortner.v1.service.UrlShortenerService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;

@RestController
@RequestMapping("/api")
public class ShortenUrlRestController {

    private final UrlShortenerService urlShortenerService;
    private final ProxyManager<byte[]> proxyManager;

    public ShortenUrlRestController(UrlShortenerService urlShortenerService,
                                    ProxyManager<byte[]> proxyManager) {
        this.urlShortenerService = urlShortenerService;
        this.proxyManager = proxyManager;
    }

    @PostMapping("/shorten")
    public ResponseEntity<?> shorten(@RequestParam String originalUrl, HttpServletRequest request) {
        // Use client IP as the bucket key
        String clientIp = request.getRemoteAddr();

        // Define bucket configuration: 10 requests per minute
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1))))
                .build();

        // Build or retrieve bucket for this client
        Bucket bucket = proxyManager.builder()
                .build(clientIp.getBytes(), configuration);

        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded. Try again later.");
        }

        ShortUrl shortUrl = urlShortenerService.createShortUrl(originalUrl);

        String fullShortUrl = ServletUriComponentsBuilder
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

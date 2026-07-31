package com.urlShortner.v1.controller;

import com.urlShortner.v1.dto.ShortenUrlResponse;
import com.urlShortner.v1.entity.ShortUrl;
import com.urlShortner.v1.service.UrlShortenerService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

@Controller
public class ShortenUrlController {

    private final UrlShortenerService urlShortenerService;
    private final ProxyManager<byte[]> proxyManager;

    public ShortenUrlController(UrlShortenerService urlShortenerService,
                                ProxyManager<byte[]> proxyManager) {
        this.urlShortenerService = urlShortenerService;
        this.proxyManager = proxyManager;
    }

    @GetMapping("/")
    public String showForm() {
        return "index";
    }

    @PostMapping("/shorten")
    public String shortenUrl(@RequestParam String originalUrl, Model model) {
        // Define a bucket configuration: 10 requests per minute
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1))))
                .build();

        // Use a fixed key for demo; in real apps use userId/IP/etc.
        Bucket bucket = proxyManager.builder()
                .build("shorten-endpoint".getBytes(), configuration);

        if (!bucket.tryConsume(1)) {
            // Rate limit exceeded
            model.addAttribute("error", "Rate limit exceeded. Try again later.");
            return "index";
        }

        ShortUrl shortUrl = urlShortenerService.createShortUrl(originalUrl);

        String fullShortUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/")
                .path(shortUrl.getShortUrl())
                .build()
                .toUriString();

        model.addAttribute("result", new ShortenUrlResponse(
                fullShortUrl,
                shortUrl.getOriginalUrl(),
                shortUrl.getCreatedAt(),
                shortUrl.getExpiresAt()));
        return "index";
    }

    @GetMapping("/{shortUrl:[a-zA-Z0-9]{6}}")
    public ResponseEntity<Void> redirectToOriginal(@PathVariable String shortUrl) {
        String originalUrl = urlShortenerService.getOriginalUrl(shortUrl);
        if (originalUrl == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}

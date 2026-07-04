package com.urlShortner.v1.controller;

import com.urlShortner.v1.dto.ShortenUrlResponse;
import com.urlShortner.v1.entity.ShortUrl;
import com.urlShortner.v1.service.UrlShortenerService;
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

@Controller
public class ShortenUrlController {

    private final UrlShortenerService urlShortenerService;

    public ShortenUrlController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @GetMapping("/")
    public String showForm() {
        return "index";
    }

    @PostMapping("/shorten")
    public String shortenUrl(@RequestParam String originalUrl, Model model) {
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

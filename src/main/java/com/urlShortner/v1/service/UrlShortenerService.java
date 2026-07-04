package com.urlShortner.v1.service;

import com.urlShortner.v1.entity.ShortUrl;
import com.urlShortner.v1.repo.UrlRepo;
import com.urlShortner.v1.util.ShortCodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UrlShortenerService {

    private static final int MAX_COLLISION_RETRIES = 10;

    private final UrlRepo urlRepo;
    private final ShortCodeGenerator shortCodeGenerator;

    public UrlShortenerService(UrlRepo urlRepo, ShortCodeGenerator shortCodeGenerator) {
        this.urlRepo = urlRepo;
        this.shortCodeGenerator = shortCodeGenerator;
    }

    @Transactional
    public ShortUrl createShortUrl(String originalUrl) {
        ShortUrl existing = urlRepo.findByOriginalUrl(originalUrl);
        if (existing != null) {
            return existing;
        }

        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl(originalUrl);
        shortUrl.setShortUrl(generateUniqueShortUrl());

        return urlRepo.save(shortUrl);
    }

    @Transactional(readOnly = true)
    public String getOriginalUrl(String shortUrl) {
        ShortUrl entry = urlRepo.findByShortUrl(shortUrl);
        if (entry == null) {
            return null;
        }
        if (entry.getExpiresAt() != null && entry.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return entry.getOriginalUrl();
    }

    private String generateUniqueShortUrl() {
        for (int i = 0; i < MAX_COLLISION_RETRIES; i++) {
            String code = shortCodeGenerator.generate();
            if (urlRepo.findByShortUrl(code) == null) {
                return code;
            }
        }
        throw new IllegalStateException(
                "Failed to generate unique short URL after " + MAX_COLLISION_RETRIES + " attempts");
    }
}

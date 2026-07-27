package com.urlShortner.v1.service;

import com.urlShortner.v1.entity.ShortUrl;
import com.urlShortner.v1.repo.UrlRepo;
import com.urlShortner.v1.util.ShortCodeGenerator;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UrlShortenerService {

    private static final int MAX_COLLISION_RETRIES = 10;

    private final UrlRepo urlRepo;
    private final ShortCodeGenerator shortCodeGenerator;

    public UrlShortenerService(UrlRepo urlRepo,
                               ShortCodeGenerator shortCodeGenerator) {
        this.urlRepo = urlRepo;
        this.shortCodeGenerator = shortCodeGenerator;
    }

    @Transactional
    public ShortUrl createShortUrl(String originalUrl) {

        ShortUrl existing = urlRepo.findByOriginalUrl(originalUrl);

        if (existing != null) {
            // Warm the cache with the already-stored original URL string
            warmCache(existing.getShortUrl(), existing.getOriginalUrl());
            return existing;
        }

        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl(originalUrl);
        shortUrl.setShortUrl(generateUniqueShortUrl());

        ShortUrl saved = urlRepo.save(shortUrl);

        // Warm the cache after the entity is persisted
        warmCache(saved.getShortUrl(), saved.getOriginalUrl());

        return saved;
    }

    @CachePut(value = "urls", key = "#shortCode")
    public String warmCache(String shortCode, String originalUrl) {
        return originalUrl;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "urls", key = "#shortUrl", unless = "#result == null")
    public String getOriginalUrl(String shortUrl) {

        System.out.println("Fetching from MySQL...");

        ShortUrl entry = urlRepo.findByShortUrl(shortUrl);

        if (entry == null) {
            return null;
        }

        if (entry.getExpiresAt() != null &&
                entry.getExpiresAt().isBefore(LocalDateTime.now())) {

            return null;
        }

        return entry.getOriginalUrl();
    }

    @CacheEvict(value = "urls", key = "#shortUrl")
    public void deleteCache(String shortUrl) {
        // Use this later if you implement delete URL
    }

    private String generateUniqueShortUrl() {

        for (int i = 0; i < MAX_COLLISION_RETRIES; i++) {

            String code = shortCodeGenerator.generate();

            if (urlRepo.findByShortUrl(code) == null) {
                return code;
            }
        }

        throw new IllegalStateException(
                "Failed to generate unique short URL after "
                        + MAX_COLLISION_RETRIES
                        + " attempts");
    }
}
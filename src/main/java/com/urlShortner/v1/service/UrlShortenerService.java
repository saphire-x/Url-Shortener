package com.urlShortner.v1.service;

import com.urlShortner.v1.entity.ShortUrl;
import com.urlShortner.v1.repo.UrlRepo;
import com.urlShortner.v1.util.ShortCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);
    private static final int MAX_COLLISION_RETRIES = 10;

    private final UrlRepo urlRepo;
    private final ShortCodeGenerator shortCodeGenerator;
    private final CacheManager cacheManager;

    public UrlShortenerService(UrlRepo urlRepo,
                               ShortCodeGenerator shortCodeGenerator,
                               CacheManager cacheManager) {
        this.urlRepo = urlRepo;
        this.shortCodeGenerator = shortCodeGenerator;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public ShortUrl createShortUrl(String originalUrl) {

        String hash = hash(originalUrl);

        ShortUrl existing = urlRepo.findByOriginalUrlHash(hash);
        if (existing != null) {
            return existing;
        }

        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl(originalUrl);
        shortUrl.setOriginalUrlHash(hash);
        shortUrl.setShortUrl(generateUniqueShortUrl());

        try {
            return urlRepo.save(shortUrl);

        } catch (DataIntegrityViolationException e) {
            ShortUrl winner = urlRepo.findByOriginalUrlHash(hash);
            if (winner != null) {
                return winner;
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public String getOriginalUrl(String shortUrl) {

        Cache notFoundCache = cacheManager.getCache("urls-notfound");
        if (notFoundCache != null && notFoundCache.get(shortUrl) != null) {
            return null;
        }

        String result = getFromDbOrCache(shortUrl);

        if (result == null) {
            markAsNotFound(shortUrl);
        }
        return result;
    }

    @Cacheable(value = "urls", key = "#shortUrl", unless = "#result == null")
    public String getFromDbOrCache(String shortUrl) {

        log.debug("Cache miss — fetching {} from MySQL", shortUrl);

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

    @CachePut(value = "urls-notfound", key = "#shortUrl")
    public String markAsNotFound(String shortUrl) {
        return "1";
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
                        + MAX_COLLISION_RETRIES + " attempts");
    }

    private String hash(String originalUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(originalUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
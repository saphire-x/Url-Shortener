package com.urlShortner.v1.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
public class RateLimiterService {

    private final ProxyManager<byte[]> proxyManager;

    @Value("${ratelimit.shorten.capacity}")
    private int shortenCapacity;
    @Value("${ratelimit.shorten.refill-duration-seconds}")
    private long shortenRefillSeconds;

    @Value("${ratelimit.redirect.capacity}")
    private int redirectCapacity;
    @Value("${ratelimit.redirect.refill-duration-seconds}")
    private long redirectRefillSeconds;

    public RateLimiterService(ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    public boolean tryConsumeShorten(String clientIp) {
        return tryConsume("shorten:" + clientIp,
                shortenCapacity, Duration.ofSeconds(shortenRefillSeconds));
    }

    public boolean tryConsumeRedirect(String clientIp) {
        return tryConsume("redirect:" + clientIp,
                redirectCapacity, Duration.ofSeconds(redirectRefillSeconds));
    }

    private boolean tryConsume(String key, int capacity, Duration refillDuration) {
        byte[] hashedKey = sha256(key);

        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(capacity, refillDuration)))
                .build();

        return proxyManager.builder()
                .build(hashedKey, configSupplier)
                .tryConsume(1);
    }

    private byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
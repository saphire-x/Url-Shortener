package com.urlShortner.v1.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.urlShortner.v1.entity.ShortUrl;

public interface UrlRepo extends JpaRepository<ShortUrl, Long> {
    
    ShortUrl findByShortUrl(String shortUrl);
    ShortUrl findByOriginalUrl(String originalUrl);

}

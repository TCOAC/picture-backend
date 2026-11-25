package com.example.picturebackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight token store used by API clients that cannot keep a session cookie.
 * Tokens are deliberately kept outside the User entity and can be moved to Redis
 * later without changing the controller contract.
 */
@Service
public class LoginTokenService {
    private static final class TokenRecord {
        private final Long userId;
        private final long expiresAt;

        private TokenRecord(Long userId, long expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, TokenRecord> tokens = new ConcurrentHashMap<>();

    @Value("${app.auth.token-timeout-hours:168}")
    private long timeoutHours;

    public String issue(Long userId) {
        if (userId == null) {
            return null;
        }
        cleanupExpired();
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (UUID.randomUUID().toString() + ":" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        long timeout = Duration.ofHours(Math.max(1, timeoutHours)).toMillis();
        tokens.put(token, new TokenRecord(userId, System.currentTimeMillis() + timeout));
        return token;
    }

    public Long resolve(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        TokenRecord record = tokens.get(token.trim());
        if (record == null) {
            return null;
        }
        if (record.expiresAt <= System.currentTimeMillis()) {
            tokens.remove(token.trim(), record);
            return null;
        }
        return record.userId;
    }

    public void revoke(String token) {
        if (token != null) {
            tokens.remove(token.trim());
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }
}

package com.smarttaskmanager.auth.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Service for generating cryptographically secure tokens and computing SHA-256 hashes.
 *
 * <p>Raw tokens are transmitted only to the user (e.g. via email verification links),
 * while the persistence store stores only their SHA-256 hashes, ensuring that database
 * leaks do not expose valid active verification or reset tokens.</p>
 */
@Service
public class TokenService {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a 32-byte cryptographically secure random token encoded in URL-safe Base64.
     */
    public String generateRawToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Computes a deterministic SHA-256 hash of the given raw token, returned as a 64-char hex string.
     */
    public String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token to hash must not be null or blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in JVM", e);
        }
    }
}

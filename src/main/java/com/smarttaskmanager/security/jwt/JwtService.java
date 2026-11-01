package com.smarttaskmanager.security.jwt;

import com.smarttaskmanager.common.config.ApplicationProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Service for creating, parsing, and validating JWT access tokens.
 *
 * <p>Uses HMAC-SHA256 signing with a secret key derived from the application configuration.
 * Tokens contain the user's email as the subject and their authorization roles as claims.</p>
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;

    public JwtService(ApplicationProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret must be configured via app.jwt.secret");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = properties.jwt().accessTokenExpirationMs();
    }

    /**
     * Generates a JWT access token for the given user.
     *
     * @param userDetails the authenticated user details
     * @return a signed JWT string
     */
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = Map.of(
                "roles", userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()
        );
        return buildToken(claims, userDetails.getUsername(), accessTokenExpirationMs);
    }

    /**
     * Generates a JWT with custom claims and expiration.
     *
     * @param extraClaims additional claims to include
     * @param subject     typically the user's email
     * @param expirationMs token lifetime in milliseconds
     * @return a signed JWT string
     */
    public String generateTokenWithExpiration(Map<String, Object> extraClaims, String subject, long expirationMs) {
        return buildToken(extraClaims, subject, expirationMs);
    }

    /**
     * Extracts the email (subject) from a JWT token.
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts a specific claim from the token using a selector function.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Validates whether the given token is valid for the specified user.
     *
     * @param token       the JWT string
     * @param userDetails the user details to validate against
     * @return true if the token is valid and belongs to the user
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * Checks whether the token has expired.
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expirationMs) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new JwtException("JWT token has expired", e);
        } catch (JwtException e) {
            throw new JwtException("Invalid JWT token", e);
        }
    }
}

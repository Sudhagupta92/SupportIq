package com.supportiq.service;

import com.supportiq.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service responsible for creating, signing, parsing, and validating JSON Web Tokens (JWT).
 * 
 * WHY JJWT 0.12.x:
 * In JJWT 0.12.x, the API was modernized to use strongly-typed SecretKey instances,
 * parser builders with verifyWith(), and getPayload() instead of deprecated parser methods.
 * HMAC-SHA256 requires at least 256 bits (32 bytes) of cryptographic key material.
 */
@Slf4j
@Service
public class JwtService {

    private final String secretKey;
    private final long jwtExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secretKey,
            @Value("${app.jwt.expiration-ms:86400000}") long jwtExpirationMs) {
        // Enforce the requirement that the secret key must be at least 32 characters (256 bits)
        if (secretKey == null || secretKey.trim().length() < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 32 characters long to ensure cryptographic security.");
        }
        this.secretKey = secretKey;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    /**
     * Generates a signed JWT containing subject (email) and custom claims (role and userId).
     *
     * WHY:
     * Embedding role and userId directly in the JWT payload reduces database round-trips
     * for basic authorization and identification during incoming requests.
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId());
        return createToken(claims, user.getEmail());
    }

    public String generateToken(Map<String, Object> extraClaims, String subject) {
        return createToken(extraClaims, subject);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts the subject (email) from the JWT.
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the user ID from the JWT claims.
     */
    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object userId = claims.get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    /**
     * Extracts the user role from the JWT claims.
     */
    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Validates whether the token belongs to the given user and has not expired.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String email = extractEmail(token);
            return (email.equalsIgnoreCase(userDetails.getUsername()) && !isTokenExpired(token));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the token expiration date has passed.
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Parses and verifies the JWT signature using the HMAC-SHA key.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Constructs a SecretKey suitable for HMAC-SHA algorithms using UTF-8 bytes.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = this.secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

package com.cmrt.pfe.security;

import com.cmrt.pfe.models.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Issues and validates the signed tokens the Angular client sends on every call.
 * Claims carry enough identity (id, role, name) that most endpoints never need a
 * database round-trip just to know who is asking.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getId())
                .claim("email", user.getEmail())
                .claim("role", user.getRole() != null ? user.getRole().name() : null)
                .claim("name", user.getFullName())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Returns the principal encoded in the token, or {@code null} if it is invalid or expired. */
    public AuthPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AuthPrincipal(
                    claims.getSubject(),
                    claims.get("email", String.class),
                    claims.get("role", String.class),
                    claims.get("name", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}

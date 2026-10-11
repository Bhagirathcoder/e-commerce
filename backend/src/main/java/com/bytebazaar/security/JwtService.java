package com.bytebazaar.security;

import com.bytebazaar.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expiryMs;

    public JwtService(@Value("${bytebazaar.jwt.secret}") String secret,
                      @Value("${bytebazaar.jwt.expiry-hours:24}") long hours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMs = hours * 3600_000L;
    }

    public String generate(User u) {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject(String.valueOf(u.getId())).claim("role", u.getRole().name())
                .issuedAt(new Date(now)).expiration(new Date(now + expiryMs)).signWith(key).compact();
    }

    /** @return user id, or null when the token is invalid/expired */
    public Long parseUserId(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Long.valueOf(c.getSubject());
        } catch (Exception e) {
            return null;
        }
    }
}

package com.rpacloud.common.security;

import java.util.Date;

import javax.crypto.SecretKey;

import com.rpacloud.common.config.RpaProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expireMillis;

    public JwtTokenProvider(RpaProperties props) {
        byte[] keyBytes = props.getAuth().getSecretKey().getBytes();
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expireMillis = props.getAuth().getTokenExpireMinutes() * 60_000L;
    }

    public String createToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMillis);
        return Jwts.builder()
                .subject(email)
                .claim("user_id", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validate(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public String getEmail(String token) {
        return parseToken(token).getSubject();
    }
}

package com.rpacloud.llm.service;

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
public class InternalTokenProvider {

    private final SecretKey key;
    private final int processTimeoutSeconds;

    private static final String DEFAULT_SECRET = "change-me-internal-secret-at-least-32-chars!!";

    public InternalTokenProvider(RpaProperties props) {
        String secret = props.getLlm().getInternalTokenSecret();
        if (DEFAULT_SECRET.equals(secret)) {
            log.warn("Using default internal token secret — set rpa.llm.internal-token-secret in production!");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.processTimeoutSeconds = props.getExecution().getProcessTimeoutSeconds();
    }

    public String createToken(Long userId, Long flowId, String executionId) {
        long ttlMs = (processTimeoutSeconds + 60L) * 1000L;
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMs);
        return Jwts.builder()
                .subject(executionId)
                .claim("user_id", userId)
                .claim("flow_id", flowId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims validateAndParse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid internal token: {}", e.getMessage());
            return null;
        }
    }
}

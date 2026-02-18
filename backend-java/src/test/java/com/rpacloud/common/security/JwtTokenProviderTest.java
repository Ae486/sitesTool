package com.rpacloud.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpacloud.common.config.RpaProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        RpaProperties props = new RpaProperties();
        props.getAuth().setSecretKey("test-secret-key-at-least-32-characters-long!!");
        props.getAuth().setTokenExpireMinutes(60);
        provider = new JwtTokenProvider(props);
    }

    @Test
    void createAndValidateRoundTrip() {
        String token = provider.createToken(1L, "user@test.com");
        assertThat(provider.validate(token)).isTrue();

        Claims claims = provider.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("user@test.com");
        assertThat(claims.get("user_id", Long.class)).isEqualTo(1L);
    }

    @Test
    void getEmailReturnsSubject() {
        String token = provider.createToken(1L, "admin@test.com");
        assertThat(provider.getEmail(token)).isEqualTo("admin@test.com");
    }

    @Test
    void invalidTokenReturnsFalse() {
        assertThat(provider.validate("invalid.token.here")).isFalse();
        assertThat(provider.validate("")).isFalse();
        assertThat(provider.validate(null)).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        RpaProperties props = new RpaProperties();
        props.getAuth().setSecretKey("test-secret-key-at-least-32-characters-long!!");
        props.getAuth().setTokenExpireMinutes(0);
        JwtTokenProvider zeroMinProvider = new JwtTokenProvider(props);

        String token = zeroMinProvider.createToken(1L, "expired@test.com");
        // Token with 0 minute expiry should be expired immediately or within ms
        // This test may be flaky if clock precision allows it; accepted trade-off
        assertThat(zeroMinProvider.validate(token)).isFalse();
    }
}

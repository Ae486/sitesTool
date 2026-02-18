package com.rpacloud.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpacloud.common.config.RpaProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InternalTokenProviderTest {

    private InternalTokenProvider provider;

    @BeforeEach
    void setUp() {
        RpaProperties props = new RpaProperties();
        props.getLlm().setInternalTokenSecret("test-secret-key-that-is-at-least-32-characters-long!!");
        props.getExecution().setProcessTimeoutSeconds(300);
        provider = new InternalTokenProvider(props);
    }

    @Test
    void createAndValidateToken() {
        String token = provider.createToken(1L, 42L, "exec-001");

        Claims claims = provider.validateAndParse(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("exec-001");
        assertThat(claims.get("user_id", Long.class)).isEqualTo(1L);
        assertThat(claims.get("flow_id", Long.class)).isEqualTo(42L);
    }

    @Test
    void invalidTokenReturnsNull() {
        Claims claims = provider.validateAndParse("invalid.jwt.token");
        assertThat(claims).isNull();
    }

    @Test
    void nullTokenReturnsNull() {
        Claims claims = provider.validateAndParse(null);
        assertThat(claims).isNull();
    }

    @Test
    void differentSecretRejectsToken() {
        String token = provider.createToken(1L, 42L, "exec-002");

        RpaProperties otherProps = new RpaProperties();
        otherProps.getLlm().setInternalTokenSecret("different-secret-key-that-is-at-least-32-chars!!");
        otherProps.getExecution().setProcessTimeoutSeconds(300);
        InternalTokenProvider other = new InternalTokenProvider(otherProps);

        Claims claims = other.validateAndParse(token);
        assertThat(claims).isNull();
    }
}

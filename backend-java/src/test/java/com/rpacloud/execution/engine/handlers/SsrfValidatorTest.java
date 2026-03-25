package com.rpacloud.execution.engine.handlers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SsrfValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/admin",
            "http://localhost/secret",
            "http://10.0.0.1/internal",
            "http://172.16.0.1/private",
            "http://192.168.1.1/home",
            "http://169.254.169.254/metadata",
            "http://0.0.0.0/",
            "http://[::1]/ipv6"
    })
    void blocksPrivateAddresses(String url) {
        assertThatThrownBy(() -> SsrfValidator.validate(url))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void blocksNonHttpSchemes() {
        assertThatThrownBy(() -> SsrfValidator.validate("ftp://example.com/file"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Only http/https");
    }

    @Test
    void blocksMissingScheme() {
        assertThatThrownBy(() -> SsrfValidator.validate("not-a-url"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void allowsPublicUrl() {
        // httpbin.org is a well-known public domain
        assertThatCode(() -> SsrfValidator.validate("https://httpbin.org/get"))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsHttpsPublicUrl() {
        assertThatCode(() -> SsrfValidator.validate("https://api.github.com"))
                .doesNotThrowAnyException();
    }
}

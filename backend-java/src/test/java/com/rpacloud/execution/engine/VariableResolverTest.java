package com.rpacloud.execution.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class VariableResolverTest {

    @Test
    void resolveDoubleBraces() {
        String result = VariableResolver.resolve("Hello {{name}}", Map.of("name", "World"));
        assertThat(result).isEqualTo("Hello World");
    }

    @Test
    void resolveDollarBraces() {
        String result = VariableResolver.resolve("url=${base}/path", Map.of("base", "https://example.com"));
        assertThat(result).isEqualTo("url=https://example.com/path");
    }

    @Test
    void bothSyntaxesTogether() {
        String result = VariableResolver.resolve("{{a}}-${b}", Map.of("a", "1", "b", "2"));
        assertThat(result).isEqualTo("1-2");
    }

    @Test
    void nullAndEmptyPassThrough() {
        assertThat(VariableResolver.resolve(null, Map.of("x", "y"))).isNull();
        assertThat(VariableResolver.resolve("no vars", Map.of())).isEqualTo("no vars");
    }

    @Test
    void nonStringResolveAny() {
        assertThat(VariableResolver.resolveAny(42, Map.of())).isEqualTo(42);
        assertThat(VariableResolver.resolveAny("{{x}}", Map.of("x", "done"))).isEqualTo("done");
    }
}

package com.rpacloud.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBizExceptionReturnsCorrectStatusAndBody() {
        BizException ex = new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Flow not found");
        ResponseEntity<Map<String, String>> response = handler.handleBizException(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).containsEntry("code", "RESOURCE_NOT_FOUND");
        assertThat(response.getBody()).containsEntry("message", "Flow not found");
    }

    @Test
    void handleGenericExceptionReturns500() {
        ResponseEntity<Map<String, String>> response = handler.handleGeneric(new RuntimeException("oops"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(response.getBody()).containsEntry("message", "Internal server error");
    }
}

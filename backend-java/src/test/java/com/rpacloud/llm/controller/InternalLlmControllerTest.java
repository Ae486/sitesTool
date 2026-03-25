package com.rpacloud.llm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.llm.dto.InternalLlmRequest;
import com.rpacloud.llm.dto.LlmChatResponse;
import com.rpacloud.llm.service.AccountService;
import com.rpacloud.llm.service.InternalTokenProvider;
import com.rpacloud.llm.service.LlmGatewayService;
import com.rpacloud.llm.service.RateLimiter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class InternalLlmControllerTest {

    @Mock private InternalTokenProvider tokenProvider;
    @Mock private LlmGatewayService llmGatewayService;
    @Mock private AccountService accountService;
    @Mock private RateLimiter rateLimiter;

    private InternalLlmController controller;

    @BeforeEach
    void setUp() {
        RpaProperties props = new RpaProperties();
        controller = new InternalLlmController(tokenProvider, llmGatewayService, accountService, rateLimiter, props);
    }

    private Claims makeClaims(Long userId, String executionId) {
        return new DefaultClaims(Map.of("sub", executionId, "user_id", userId));
    }

    private InternalLlmRequest makeRequest(String token, String prompt) {
        InternalLlmRequest req = new InternalLlmRequest();
        req.setInternalToken(token);
        req.setPrompt(prompt);
        return req;
    }

    @Test
    void chatSuccess() {
        when(tokenProvider.validateAndParse("valid-token")).thenReturn(makeClaims(1L, "exec-1"));
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);
        doThrow(new BizException(ErrorCode.RESOURCE_NOT_FOUND, "no account"))
                .when(accountService).freeze(anyLong(), anyString(), anyLong());
        when(llmGatewayService.chat(any())).thenReturn(new LlmChatResponse("result", 50L, "model"));

        LlmChatResponse resp = controller.chat(makeRequest("valid-token", "Hello"));

        assertThat(resp.getResponse()).isEqualTo("result");
        assertThat(resp.getTokensUsed()).isEqualTo(50L);
    }

    @Test
    void chatWithBillingSettles() {
        when(tokenProvider.validateAndParse("valid-token")).thenReturn(makeClaims(1L, "exec-1"));
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);
        doNothing().when(accountService).freeze(anyLong(), anyString(), anyLong());
        when(llmGatewayService.chat(any())).thenReturn(new LlmChatResponse("ok", 100L, "gpt-4o"));

        controller.chat(makeRequest("valid-token", "Hello"));

        verify(accountService).settle(eq(1L), anyString(), eq(100L), anyLong());
        verify(accountService, never()).refund(anyLong(), anyString(), anyLong());
    }

    @Test
    void chatWithBillingRefundsOnError() {
        when(tokenProvider.validateAndParse("valid-token")).thenReturn(makeClaims(1L, "exec-1"));
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);
        doNothing().when(accountService).freeze(anyLong(), anyString(), anyLong());
        when(llmGatewayService.chat(any())).thenThrow(new BizException(ErrorCode.LLM_SERVICE_ERROR, "fail"));

        assertThatThrownBy(() -> controller.chat(makeRequest("valid-token", "Hello")))
                .isInstanceOf(BizException.class);

        verify(accountService).refund(eq(1L), anyString(), anyLong());
        verify(accountService, never()).settle(anyLong(), anyString(), anyLong(), anyLong());
    }

    @Test
    void chatRejectsInvalidToken() {
        when(tokenProvider.validateAndParse("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> controller.chat(makeRequest("bad-token", "Hello")))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INTERNAL_TOKEN));
    }

    @Test
    void chatRejectsMalformedClaims() {
        DefaultClaims claims = new DefaultClaims(Map.of("sub", "exec-1"));
        when(tokenProvider.validateAndParse("token")).thenReturn(claims);

        assertThatThrownBy(() -> controller.chat(makeRequest("token", "Hello")))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INTERNAL_TOKEN));
    }

    @Test
    void chatMaxTokensZeroFloorAtOne() {
        when(tokenProvider.validateAndParse("valid-token")).thenReturn(makeClaims(1L, "exec-1"));
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);
        doNothing().when(accountService).freeze(anyLong(), anyString(), anyLong());
        when(llmGatewayService.chat(any())).thenReturn(new LlmChatResponse("ok", 10L, "model"));

        InternalLlmRequest req = makeRequest("valid-token", "Hello");
        req.setMaxTokens(0);
        controller.chat(req);

        // freeze should be called with 1 (floor), not 0
        verify(accountService).freeze(eq(1L), anyString(), eq(1L));
    }

    @Test
    void chatRejectsWhenRateLimited() {
        when(tokenProvider.validateAndParse("valid-token")).thenReturn(makeClaims(1L, "exec-1"));
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> controller.chat(makeRequest("valid-token", "Hello")))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.LLM_QUOTA_EXCEEDED));
    }

    // --- credential endpoint tests ---

    @Test
    void credentialSuccess() {
        when(tokenProvider.validateAndParse("valid-token")).thenReturn(makeClaims(1L, "exec-1"));

        // Use reflection to set @Value fields since no Spring context
        setField(controller, "llmApiKey", "sk-test");
        setField(controller, "llmBaseUrl", "https://api.example.com");
        setField(controller, "llmDefaultModel", "gpt-4o");

        Map<String, String> result = controller.credential(Map.of("internal_token", "valid-token"));

        assertThat(result).containsEntry("api_key", "sk-test");
        assertThat(result).containsEntry("base_url", "https://api.example.com");
        assertThat(result).containsEntry("model", "gpt-4o");
    }

    @Test
    void credentialRejectsInvalidToken() {
        when(tokenProvider.validateAndParse("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> controller.credential(Map.of("internal_token", "bad-token")))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INTERNAL_TOKEN));
    }

    @Test
    void credentialRejectsMissingToken() {
        assertThatThrownBy(() -> controller.credential(Map.of()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_INTERNAL_TOKEN));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}

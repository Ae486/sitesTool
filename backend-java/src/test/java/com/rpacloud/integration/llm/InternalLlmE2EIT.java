package com.rpacloud.integration.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.integration.BaseIntegrationTest;
import com.rpacloud.llm.entity.Account;
import com.rpacloud.llm.repository.AccountRepository;
import com.rpacloud.llm.repository.TransactionRepository;
import com.rpacloud.llm.service.AccountService;
import com.rpacloud.llm.service.InternalTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

@AutoConfigureMockMvc
class InternalLlmE2EIT extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private InternalTokenProvider tokenProvider;
    @Autowired private AccountService accountService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ChatModel chatModel;

    @BeforeEach
    void cleanUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        var keys = redisTemplate.keys("rpa:ratelimit:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
    }

    private void setupMockLlm(String content, int tokens) {
        AssistantMessage msg = new AssistantMessage(content);
        Generation generation = new Generation(msg);
        Usage usage = mock(Usage.class);
        when(usage.getTotalTokens()).thenReturn(tokens);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);
        when(metadata.getModel()).thenReturn("test-model");
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    private String makeBody(String token, String prompt) throws Exception {
        return objectMapper.writeValueAsString(Map.of("prompt", prompt, "internal_token", token));
    }

    @Test
    void fullChainWithBilling() throws Exception {
        accountService.charge(1L, 10000L);
        setupMockLlm("Hello from LLM", 500);
        String token = tokenProvider.createToken(1L, 1L, "exec-1");

        mockMvc.perform(post("/internal/llm/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(makeBody(token, "Hello")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Hello from LLM"))
                .andExpect(jsonPath("$.tokens_used").value(500))
                .andExpect(jsonPath("$.model").value("test-model"));

        Account account = accountService.getAccount(1L);
        assertThat(account.getBalance()).isEqualTo(9500L);
        assertThat(account.getFrozen()).isEqualTo(0L);
    }

    @Test
    void fullChainWithoutBilling() throws Exception {
        setupMockLlm("No billing", 100);
        String token = tokenProvider.createToken(1L, 1L, "exec-2");

        mockMvc.perform(post("/internal/llm/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(makeBody(token, "Hello")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("No billing"));
    }

    @Test
    void llmErrorRefundsFrozenTokens() throws Exception {
        accountService.charge(1L, 10000L);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM down"));
        String token = tokenProvider.createToken(1L, 1L, "exec-3");

        mockMvc.perform(post("/internal/llm/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(makeBody(token, "Hello")))
                .andExpect(status().is(502));

        Account account = accountService.getAccount(1L);
        assertThat(account.getBalance()).isEqualTo(10000L);
        assertThat(account.getFrozen()).isEqualTo(0L);
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(post("/internal/llm/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(makeBody("invalid-token", "Hello")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_TOKEN"));
    }

    @Test
    void noTokenReturns401() throws Exception {
        mockMvc.perform(post("/internal/llm/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_TOKEN"));
    }

    @Test
    void noSpringSecurityJwtRequired() throws Exception {
        setupMockLlm("ok", 10);
        String internalToken = tokenProvider.createToken(1L, 1L, "exec-no-jwt");

        // No Bearer JWT header, only internal token in body — should still succeed
        mockMvc.perform(post("/internal/llm/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(makeBody(internalToken, "Hello")))
                .andExpect(status().isOk());
    }
}

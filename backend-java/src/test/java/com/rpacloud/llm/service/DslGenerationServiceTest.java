package com.rpacloud.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.AssistantMessage;

@ExtendWith(MockitoExtension.class)
class DslGenerationServiceTest {

    @Mock private ChatModel chatModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void setupReturn(String content) {
        AssistantMessage msg = new AssistantMessage(content);
        Generation generation = new Generation(msg);
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    @Test
    void generateDslReturnsMap() {
        setupReturn("{\"steps\":[{\"type\":\"navigate\",\"url\":\"https://example.com\"}]}");
        DslGenerationService service = new DslGenerationService(chatModel, objectMapper);

        Map<String, Object> dsl = service.generateDsl("Open example.com");

        assertThat(dsl).containsKey("steps");
    }

    @Test
    void generateDslStripsMarkdownCodeFence() {
        setupReturn("```json\n{\"steps\":[]}\n```");
        DslGenerationService service = new DslGenerationService(chatModel, objectMapper);

        Map<String, Object> dsl = service.generateDsl("test");

        assertThat(dsl).containsKey("steps");
    }

    @Test
    void generateDslStripsGenericCodeFence() {
        setupReturn("```\n{\"steps\":[]}\n```");
        DslGenerationService service = new DslGenerationService(chatModel, objectMapper);

        Map<String, Object> dsl = service.generateDsl("test");

        assertThat(dsl).containsKey("steps");
    }

    @Test
    void generateDslThrowsOnNullResponse() {
        AssistantMessage msg = new AssistantMessage(null);
        Generation generation = new Generation(msg);
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        DslGenerationService service = new DslGenerationService(chatModel, objectMapper);

        assertThatThrownBy(() -> service.generateDsl("test"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.LLM_SERVICE_ERROR));
    }

    @Test
    void generateDslThrowsOnInvalidJson() {
        setupReturn("not valid json");
        DslGenerationService service = new DslGenerationService(chatModel, objectMapper);

        assertThatThrownBy(() -> service.generateDsl("test"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.LLM_SERVICE_ERROR));
    }
}

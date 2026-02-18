package com.rpacloud.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.llm.dto.LlmChatRequest;
import com.rpacloud.llm.dto.LlmChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.AssistantMessage;

@ExtendWith(MockitoExtension.class)
class LlmGatewayServiceTest {

    @Mock private ChatModel chatModel;

    private void setupSuccessResponse() {
        AssistantMessage msg = new AssistantMessage("Hello world");
        Generation generation = new Generation(msg);
        Usage usage = mock(Usage.class);
        when(usage.getTotalTokens()).thenReturn(100);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);
        when(metadata.getModel()).thenReturn("test-model");
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    @Test
    void chatReturnsResponse() {
        setupSuccessResponse();
        LlmGatewayService service = new LlmGatewayService(chatModel);
        LlmChatRequest req = new LlmChatRequest();
        req.setPrompt("Say hello");

        LlmChatResponse resp = service.chat(req);

        assertThat(resp.getResponse()).isEqualTo("Hello world");
        assertThat(resp.getTokensUsed()).isEqualTo(100L);
        assertThat(resp.getModel()).isEqualTo("test-model");
    }

    @Test
    void chatWithCustomOptions() {
        setupSuccessResponse();
        LlmGatewayService service = new LlmGatewayService(chatModel);
        LlmChatRequest req = new LlmChatRequest();
        req.setPrompt("Test");
        req.setModel("custom-model");
        req.setMaxTokens(500);
        req.setTemperature(0.5);

        LlmChatResponse resp = service.chat(req);

        assertThat(resp.getResponse()).isEqualTo("Hello world");
    }

    @Test
    void chatWrapsExceptionAsBizException() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("API failure"));
        LlmGatewayService service = new LlmGatewayService(chatModel);
        LlmChatRequest req = new LlmChatRequest();
        req.setPrompt("fail");

        assertThatThrownBy(() -> service.chat(req))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.LLM_SERVICE_ERROR));
    }
}

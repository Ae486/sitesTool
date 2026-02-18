package com.rpacloud.llm.service;

import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.llm.dto.LlmChatRequest;
import com.rpacloud.llm.dto.LlmChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LlmGatewayService {

    private final ChatClient chatClient;
    private final ChatModel chatModel;

    public LlmGatewayService(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.chatClient = ChatClient.create(chatModel);
    }

    public LlmChatResponse chat(LlmChatRequest req) {
        try {
            var builder = chatClient.prompt().user(req.getPrompt());

            OpenAiChatOptions.Builder optBuilder = OpenAiChatOptions.builder();
            boolean hasOptions = false;
            if (req.getModel() != null && !req.getModel().isBlank()) {
                optBuilder.model(req.getModel());
                hasOptions = true;
            }
            if (req.getMaxTokens() != null) {
                optBuilder.maxTokens(req.getMaxTokens());
                hasOptions = true;
            }
            if (req.getTemperature() != null) {
                optBuilder.temperature(req.getTemperature());
                hasOptions = true;
            }
            if (hasOptions) {
                builder = builder.options(optBuilder.build());
            }

            ChatResponse response = builder.call().chatResponse();

            String content = response.getResult().getOutput().getText();
            long totalTokens = response.getMetadata().getUsage().getTotalTokens();
            String modelUsed = response.getMetadata().getModel() != null
                    ? response.getMetadata().getModel()
                    : (req.getModel() != null ? req.getModel() : "default");

            log.info("LLM chat completed: model={}, tokens={}", modelUsed, totalTokens);
            return new LlmChatResponse(content, totalTokens, modelUsed);

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM service error: {}", e.getMessage(), e);
            throw new BizException(ErrorCode.LLM_SERVICE_ERROR, "LLM service unavailable");
        }
    }
}

package com.rpacloud.llm.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DslGenerationService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public DslGenerationService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.create(chatModel);
        this.objectMapper = objectMapper;
        this.systemPrompt = loadSystemPrompt();
    }

    public Map<String, Object> generateDsl(String description) {
        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(description)
                    .call()
                    .content();

            // Extract JSON from response (may be wrapped in markdown code block)
            String json = extractJson(response);
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("DSL generation failed: {}", e.getMessage(), e);
            throw new BizException(ErrorCode.LLM_SERVICE_ERROR, "DSL generation failed: " + e.getMessage());
        }
    }

    private String extractJson(String text) {
        if (text == null) throw new BizException(ErrorCode.LLM_SERVICE_ERROR, "Empty LLM response");
        // Strip markdown code fences if present
        String trimmed = text.strip();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.strip();
    }

    private String loadSystemPrompt() {
        try {
            InputStream is = getClass().getResourceAsStream("/prompts/generate-dsl-system.txt");
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Failed to load system prompt file, using default");
        }
        return DEFAULT_SYSTEM_PROMPT;
    }

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are an RPA automation expert. Generate a JSON DSL based on the user's natural language description.

            Available step types:
            - navigate: Open URL. Fields: url (required), timeout
            - click: Click element. Fields: selector (required), timeout
            - input: Input text. Fields: selector (required), value (required), clear_first
            - select: Select dropdown option. Fields: selector (required), value (required)
            - wait_for: Wait for element. Fields: selector (required), timeout
            - wait_time: Fixed wait. Fields: duration (required, milliseconds)
            - extract: Extract text from element. Fields: selector (required), save_to
            - extract_all: Extract list data. Fields: selector (required), fields, save_to
            - screenshot: Take screenshot. Fields: save_to
            - scroll: Scroll page. Fields: direction (up/down), distance
            - hover: Hover over element. Fields: selector (required)
            - keyboard: Press key. Fields: key (required)
            - set_variable: Set variable. Fields: name (required), value (required)
            - if_exists: Check element exists. Fields: selector (required), children, else_children
            - if_else: Conditional branch. Fields: condition_type, condition_variable, children, else_children
            - loop: Count loop. Fields: times (required), children
            - loop_array: Array loop. Fields: array_variable, item_variable, children
            - try_click: Try click (skip if not found). Fields: selector (required), timeout
            - eval_js: Execute JavaScript. Fields: script (required), save_to
            - llm_call: Call LLM. Fields: prompt (required), model, save_to (required)
            - capture_network: Capture network responses. Fields: url_pattern (required), save_to
            - assert_text: Assert text content. Fields: selector (required), expected (required)

            Output format (JSON only, no explanation):
            {"steps": [{"type": "...", ...}, ...]}

            Rules:
            1. Use $variable_name for variable references in field values
            2. Choose the most appropriate step types for the task
            3. Add reasonable waits between navigation and interaction steps
            4. Output ONLY valid JSON, no markdown or explanations
            """;
}

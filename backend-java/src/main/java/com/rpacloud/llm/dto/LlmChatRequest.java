package com.rpacloud.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LlmChatRequest {

    private String prompt;

    private String model;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Double temperature;
}

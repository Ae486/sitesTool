package com.rpacloud.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LlmChatResponse {

    private String response;

    @JsonProperty("tokens_used")
    private Long tokensUsed;

    private String model;
}

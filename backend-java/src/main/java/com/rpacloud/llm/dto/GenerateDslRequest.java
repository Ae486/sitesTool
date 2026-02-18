package com.rpacloud.llm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GenerateDslRequest {

    @NotBlank(message = "description is required")
    private String description;
}

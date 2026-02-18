package com.rpacloud.llm.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GenerateDslResponse {

    private Map<String, Object> dsl;
}

package com.rpacloud.execution.engine.handlers;

import java.util.Map;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HandlerResult {
    String message;
    @Builder.Default
    Map<String, Object> extractedData = Map.of();
    String screenshotPath;
    @Builder.Default
    long totalTokensUsed = 0;

    public static HandlerResult of(String message) {
        return HandlerResult.builder().message(message).build();
    }

    public static HandlerResult withData(String message, Map<String, Object> data) {
        return HandlerResult.builder().message(message).extractedData(data).build();
    }
}

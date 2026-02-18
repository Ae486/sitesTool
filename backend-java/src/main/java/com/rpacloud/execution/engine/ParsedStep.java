package com.rpacloud.execution.engine;

import java.util.Map;

import lombok.Value;

/**
 * Parsed step with validated parameters.
 */
@Value
public class ParsedStep {
    StepType type;
    Map<String, Object> params;
    String description;
}

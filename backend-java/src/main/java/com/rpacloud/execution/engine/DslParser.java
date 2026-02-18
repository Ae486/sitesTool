package com.rpacloud.execution.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses DSL JSON into validated ParsedStep list.
 * Port of Python dsl_parser.py.
 */
@Slf4j
public class DslParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<ParsedStep> parse(String dslJson) {
        Map<String, Object> dsl;
        try {
            dsl = MAPPER.readValue(dslJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new DslParseException("Invalid JSON: " + e.getMessage());
        }
        if (dsl == null || !dsl.containsKey("steps")) {
            throw new DslParseException("DSL must contain 'steps' array");
        }
        Object rawSteps = dsl.get("steps");
        if (!(rawSteps instanceof List<?> stepList)) {
            throw new DslParseException("'steps' must be an array");
        }
        List<ParsedStep> result = new java.util.ArrayList<>();
        for (int i = 0; i < stepList.size(); i++) {
            Object item = stepList.get(i);
            if (!(item instanceof Map<?, ?> stepMap)) {
                throw new DslParseException("Step " + (i + 1) + " must be a JSON object");
            }
            try {
                result.add(parseStep(asStringMap(stepMap)));
            } catch (Exception e) {
                throw new DslParseException("Error parsing step " + (i + 1) + ": " + e.getMessage());
            }
        }
        log.info("Parsed {} steps", result.size());
        return result;
    }

    private ParsedStep parseStep(Map<String, Object> step) {
        String typeStr = (String) step.get("type");
        if (typeStr == null || typeStr.isBlank()) {
            throw new DslParseException("Step must have 'type' field");
        }
        StepType type = StepType.fromValue(typeStr);
        String description = step.get("description") instanceof String s ? s : null;
        Map<String, Object> params = new HashMap<>(step);
        params.remove("type");
        params.remove("description");
        return new ParsedStep(type, params, description);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Map<?, ?> raw) {
        return raw.entrySet().stream()
                .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue));
    }

    public static class DslParseException extends RuntimeException {
        public DslParseException(String message) { super(message); }
    }
}

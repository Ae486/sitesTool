package com.rpacloud.execution.process;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracts JSON payload from subprocess stdout/stderr.
 * 3-strategy best-effort parsing mirrors Python process_output_parser.py.
 */
@Slf4j
public final class ProcessOutputParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private ProcessOutputParser() {}

    public static Map<String, Object> extractJson(String output) {
        if (output == null || output.isBlank()) return null;
        String text = output.strip();

        // 1) Direct parse
        Map<String, Object> result = tryParse(text);
        if (result != null) return result;

        // 2) Last-line parse (reverse)
        String[] lines = text.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String ln = lines[i].strip();
            if (ln.startsWith("{") && ln.endsWith("}")) {
                result = tryParse(ln);
                if (result != null) return result;
            }
        }

        // 3) Try from last '{' positions
        int idx = text.lastIndexOf('{');
        while (idx >= 0) {
            result = tryParse(text.substring(idx).strip());
            if (result != null) return result;
            idx = text.lastIndexOf('{', idx - 1);
        }

        return null;
    }

    private static Map<String, Object> tryParse(String json) {
        try {
            Object val = MAPPER.readValue(json, Object.class);
            if (val instanceof Map) {
                return MAPPER.readValue(json, MAP_TYPE);
            }
        } catch (Exception ignored) {}
        return null;
    }
}

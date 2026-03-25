package com.rpacloud.execution.worker;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * NDJSON protocol for Parent ↔ Worker subprocess communication.
 * Each message is one JSON line with a "type" discriminator.
 */
public final class WorkerProtocol {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public static final String TYPE_EXECUTE = "execute";
    public static final String TYPE_PING = "ping";
    public static final String TYPE_SHUTDOWN = "shutdown";
    public static final String TYPE_READY = "ready";
    public static final String TYPE_RESULT = "result";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_PONG = "pong";

    private WorkerProtocol() {}

    // --- Parent → Worker commands ---

    public record ExecuteCommand(String executionId, long flowId, String dslJson,
                                 String proxyUrl, String internalToken) {}

    // --- Worker → Parent events ---

    public record TaskResult(String executionId, Map<String, Object> payload) {}

    public record TaskError(String executionId, String message) {}

    // --- Serialization ---

    public static String serializeExecute(ExecuteCommand cmd) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", TYPE_EXECUTE);
        m.put("executionId", cmd.executionId());
        m.put("flowId", cmd.flowId());
        m.put("dslJson", cmd.dslJson());
        if (cmd.proxyUrl() != null) m.put("proxyUrl", cmd.proxyUrl());
        if (cmd.internalToken() != null) m.put("internalToken", cmd.internalToken());
        return toJson(m);
    }

    public static String serializePing() {
        return toJson(Map.of("type", TYPE_PING));
    }

    public static String serializeShutdown() {
        return toJson(Map.of("type", TYPE_SHUTDOWN));
    }

    public static String serializeReady() {
        return toJson(Map.of("type", TYPE_READY));
    }

    public static String serializePong() {
        return toJson(Map.of("type", TYPE_PONG));
    }

    public static String serializeResult(String executionId, Map<String, Object> payload) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", TYPE_RESULT);
        m.put("executionId", executionId);
        m.put("payload", payload);
        return toJson(m);
    }

    public static String serializeError(String executionId, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", TYPE_ERROR);
        m.put("executionId", executionId);
        m.put("message", message);
        return toJson(m);
    }

    // --- Deserialization ---

    public static Map<String, Object> deserialize(String line) {
        try {
            return MAPPER.readValue(line, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid NDJSON line: " + e.getMessage(), e);
        }
    }

    public static String getType(Map<String, Object> msg) {
        Object t = msg.get("type");
        return t != null ? t.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public static ExecuteCommand toExecuteCommand(Map<String, Object> msg) {
        return new ExecuteCommand(
                (String) msg.get("executionId"),
                ((Number) msg.get("flowId")).longValue(),
                (String) msg.get("dslJson"),
                (String) msg.get("proxyUrl"),
                (String) msg.get("internalToken"));
    }

    @SuppressWarnings("unchecked")
    public static TaskResult toTaskResult(Map<String, Object> msg) {
        return new TaskResult(
                (String) msg.get("executionId"),
                (Map<String, Object>) msg.get("payload"));
    }

    public static TaskError toTaskError(Map<String, Object> msg) {
        return new TaskError(
                (String) msg.get("executionId"),
                (String) msg.get("message"));
    }

    private static String toJson(Map<String, Object> m) {
        try {
            return MAPPER.writeValueAsString(m);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}

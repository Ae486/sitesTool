package com.rpacloud.execution.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class WorkerProtocolTest {

    @Test
    void serializeDeserialize_executeCommand() {
        var cmd = new WorkerProtocol.ExecuteCommand(
                "exec-123", 42L, "{\"steps\":[]}", "http://proxy:8080", "jwt-token");
        String json = WorkerProtocol.serializeExecute(cmd);

        Map<String, Object> parsed = WorkerProtocol.deserialize(json);
        assertThat(WorkerProtocol.getType(parsed)).isEqualTo("execute");

        WorkerProtocol.ExecuteCommand restored = WorkerProtocol.toExecuteCommand(parsed);
        assertThat(restored.executionId()).isEqualTo("exec-123");
        assertThat(restored.flowId()).isEqualTo(42L);
        assertThat(restored.dslJson()).isEqualTo("{\"steps\":[]}");
        assertThat(restored.proxyUrl()).isEqualTo("http://proxy:8080");
        assertThat(restored.internalToken()).isEqualTo("jwt-token");
    }

    @Test
    void serializeDeserialize_executeCommand_nullProxy() {
        var cmd = new WorkerProtocol.ExecuteCommand("exec-1", 1L, "{}", null, null);
        String json = WorkerProtocol.serializeExecute(cmd);

        WorkerProtocol.ExecuteCommand restored = WorkerProtocol.toExecuteCommand(
                WorkerProtocol.deserialize(json));
        assertThat(restored.proxyUrl()).isNull();
        assertThat(restored.internalToken()).isNull();
    }

    @Test
    void serializeDeserialize_ping() {
        String json = WorkerProtocol.serializePing();
        Map<String, Object> parsed = WorkerProtocol.deserialize(json);
        assertThat(WorkerProtocol.getType(parsed)).isEqualTo("ping");
    }

    @Test
    void serializeDeserialize_shutdown() {
        String json = WorkerProtocol.serializeShutdown();
        assertThat(WorkerProtocol.getType(WorkerProtocol.deserialize(json))).isEqualTo("shutdown");
    }

    @Test
    void serializeDeserialize_ready() {
        String json = WorkerProtocol.serializeReady();
        assertThat(WorkerProtocol.getType(WorkerProtocol.deserialize(json))).isEqualTo("ready");
    }

    @Test
    void serializeDeserialize_pong() {
        String json = WorkerProtocol.serializePong();
        assertThat(WorkerProtocol.getType(WorkerProtocol.deserialize(json))).isEqualTo("pong");
    }

    @Test
    void serializeDeserialize_result() {
        Map<String, Object> payload = Map.of("status", "success", "steps_executed", 3);
        String json = WorkerProtocol.serializeResult("exec-42", payload);

        Map<String, Object> parsed = WorkerProtocol.deserialize(json);
        assertThat(WorkerProtocol.getType(parsed)).isEqualTo("result");

        WorkerProtocol.TaskResult result = WorkerProtocol.toTaskResult(parsed);
        assertThat(result.executionId()).isEqualTo("exec-42");
        assertThat(result.payload()).containsEntry("status", "success");
        assertThat(result.payload()).containsEntry("steps_executed", 3);
    }

    @Test
    void serializeDeserialize_error() {
        String json = WorkerProtocol.serializeError("exec-99", "OutOfMemory");

        Map<String, Object> parsed = WorkerProtocol.deserialize(json);
        assertThat(WorkerProtocol.getType(parsed)).isEqualTo("error");

        WorkerProtocol.TaskError error = WorkerProtocol.toTaskError(parsed);
        assertThat(error.executionId()).isEqualTo("exec-99");
        assertThat(error.message()).isEqualTo("OutOfMemory");
    }

    @Test
    void singleLineOutput() {
        // NDJSON requires each message to be exactly one line
        var cmd = new WorkerProtocol.ExecuteCommand("e1", 1L, "{\"steps\":[]}", null, null);
        String json = WorkerProtocol.serializeExecute(cmd);
        assertThat(json).doesNotContain("\n");

        String result = WorkerProtocol.serializeResult("e1", Map.of("status", "ok"));
        assertThat(result).doesNotContain("\n");
    }
}

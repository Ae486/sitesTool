package com.rpacloud.execution.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ProcessOutputParserTest {

    @Test
    void directJsonParse() {
        Map<String, Object> result = ProcessOutputParser.extractJson("{\"status\":\"success\",\"steps\":3}");
        assertThat(result).containsEntry("status", "success");
    }

    @Test
    void lastLineParse() {
        String output = "INFO Starting...\nINFO Running...\n{\"status\":\"success\",\"execution_id\":\"abc\"}";
        Map<String, Object> result = ProcessOutputParser.extractJson(output);
        assertThat(result).containsEntry("status", "success").containsEntry("execution_id", "abc");
    }

    @Test
    void noisyOutputParse() {
        String output = "Some noise {\"partial\":true} more noise {\"status\":\"failed\",\"message\":\"timeout\"}";
        Map<String, Object> result = ProcessOutputParser.extractJson(output);
        assertThat(result).containsKey("status");
    }

    @Test
    void nullAndEmptyReturnsNull() {
        assertThat(ProcessOutputParser.extractJson(null)).isNull();
        assertThat(ProcessOutputParser.extractJson("")).isNull();
        assertThat(ProcessOutputParser.extractJson("   ")).isNull();
        assertThat(ProcessOutputParser.extractJson("not json at all")).isNull();
    }
}

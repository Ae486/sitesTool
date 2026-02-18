package com.rpacloud.execution.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class DslParserTest {

    private final DslParser parser = new DslParser();

    @Test
    void parseValidDsl() {
        String dsl = """
                {"steps":[
                    {"type":"navigate","url":"https://example.com"},
                    {"type":"click","selector":"#btn","description":"Click button"}
                ]}""";
        List<ParsedStep> steps = parser.parse(dsl);
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).getType()).isEqualTo(StepType.NAVIGATE);
        assertThat(steps.get(0).getParams()).containsEntry("url", "https://example.com");
        assertThat(steps.get(1).getDescription()).isEqualTo("Click button");
    }

    @Test
    void parseEmptyStepsArray() {
        List<ParsedStep> steps = parser.parse("{\"steps\":[]}");
        assertThat(steps).isEmpty();
    }

    @Test
    void invalidJsonThrows() {
        assertThatThrownBy(() -> parser.parse("not json"))
                .isInstanceOf(DslParser.DslParseException.class)
                .hasMessageContaining("Invalid JSON");
    }

    @Test
    void missingStepsThrows() {
        assertThatThrownBy(() -> parser.parse("{\"name\":\"test\"}"))
                .isInstanceOf(DslParser.DslParseException.class)
                .hasMessageContaining("steps");
    }

    @Test
    void unknownStepTypeThrows() {
        assertThatThrownBy(() -> parser.parse("{\"steps\":[{\"type\":\"unknown_type\"}]}"))
                .isInstanceOf(DslParser.DslParseException.class)
                .hasMessageContaining("Unknown step type");
    }

    @Test
    void parseControlFlowStep() {
        String dsl = """
                {"steps":[{"type":"loop","times":3,"children":[
                    {"type":"click","selector":".item"}
                ]}]}""";
        List<ParsedStep> steps = parser.parse(dsl);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getType()).isEqualTo(StepType.LOOP);
        assertThat(steps.get(0).getParams()).containsKey("children");
    }
}

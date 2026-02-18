package com.rpacloud.integration.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.rpacloud.execution.engine.DslParser;
import com.rpacloud.execution.engine.ParsedStep;
import com.rpacloud.execution.engine.PlaywrightExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PlaywrightExecutorE2EIT extends PlaywrightBaseIT {

    private DslParser parser;

    @Override
    @BeforeEach
    void setUp() {
        // Don't call super — executor manages its own browser lifecycle
        parser = new DslParser();
        variables = new java.util.HashMap<>();
    }

    @Override
    @AfterEach
    void tearDown() {
        // No context to close
    }

    @Test
    void fullFlow_success() {
        String dsl = """
                {"steps":[
                    {"type":"navigate","url":"%s/test-page.html"},
                    {"type":"click","selector":"#btn-click"},
                    {"type":"extract","selector":"#btn-click","variable":"btn_text"},
                    {"type":"assert_text","selector":"#btn-click","expected":"Clicked!"}
                ]}""".formatted(baseUrl);

        List<ParsedStep> steps = parser.parse(dsl);
        var executor = new PlaywrightExecutor(true, "chromium", null, screenshotDir);
        var result = executor.execute(1L, steps);

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getStepsFailed()).isZero();
        assertThat(result.getStepsExecuted()).isEqualTo(4);
        assertThat(result.getVariables()).containsEntry("btn_text", "Clicked!");
    }

    @Test
    void withLoop_iteratesCorrectly() {
        String dsl = """
                {"steps":[
                    {"type":"navigate","url":"%s/test-page.html"},
                    {"type":"loop","times":3,"children":[
                        {"type":"click","selector":"#btn-counter"}
                    ]},
                    {"type":"extract","selector":"#counter","variable":"count"}
                ]}""".formatted(baseUrl);

        List<ParsedStep> steps = parser.parse(dsl);
        var executor = new PlaywrightExecutor(true, "chromium", null, screenshotDir);
        var result = executor.execute(2L, steps);

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getVariables()).containsEntry("count", "3");
    }

    @Test
    void withIfElse_takesCorrectBranch() {
        String dsl = """
                {"steps":[
                    {"type":"navigate","url":"%s/test-page.html"},
                    {"type":"set_variable","variable":"flag","value":true},
                    {"type":"if_else","condition_type":"variable_truthy","condition_variable":"flag",
                     "children":[{"type":"click","selector":"#btn-click"}],
                     "else_children":[{"type":"click","selector":"#nonexistent"}]},
                    {"type":"extract","selector":"#btn-click","variable":"btn_text"}
                ]}""".formatted(baseUrl);

        List<ParsedStep> steps = parser.parse(dsl);
        var executor = new PlaywrightExecutor(true, "chromium", null, screenshotDir);
        var result = executor.execute(3L, steps);

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getVariables()).containsEntry("btn_text", "Clicked!");
    }

    @Test
    void failingStep_recordsError() {
        String dsl = """
                {"steps":[
                    {"type":"navigate","url":"%s/test-page.html"},
                    {"type":"click","selector":"#nonexistent","timeout":500}
                ]}""".formatted(baseUrl);

        List<ParsedStep> steps = parser.parse(dsl);
        var executor = new PlaywrightExecutor(true, "chromium", null, screenshotDir);
        var result = executor.execute(4L, steps);

        assertThat(result.getStatus()).isIn("partial", "failed");
        assertThat(result.getStepsFailed()).isPositive();
        assertThat(result.getStepResults().get(1).getError()).isNotNull();
    }
}

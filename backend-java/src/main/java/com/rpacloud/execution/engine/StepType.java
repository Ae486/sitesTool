package com.rpacloud.execution.engine;

/**
 * Step types derived from dslSchema.json.
 * Mirrors Python's dynamically-generated StepType enum.
 */
public enum StepType {
    NAVIGATE("navigate"),
    CLICK("click"),
    INPUT("input"),
    WAIT_FOR("wait_for"),
    WAIT_TIME("wait_time"),
    EXTRACT("extract"),
    SCREENSHOT("screenshot"),
    SELECT("select"),
    CHECKBOX("checkbox"),
    SCROLL("scroll"),
    HOVER("hover"),
    KEYBOARD("keyboard"),
    SET_VARIABLE("set_variable"),
    IF_EXISTS("if_exists"),
    ASSERT_TEXT("assert_text"),
    ASSERT_VISIBLE("assert_visible"),
    EXTRACT_ALL("extract_all"),
    RANDOM_DELAY("random_delay"),
    TRY_CLICK("try_click"),
    EVAL_JS("eval_js"),
    NEW_TAB("new_tab"),
    SWITCH_TAB("switch_tab"),
    CLOSE_TAB("close_tab"),
    LOOP("loop"),
    LOOP_ARRAY("loop_array"),
    IF_ELSE("if_else"),
    DRAG_DROP("drag_drop"),
    UPLOAD_FILE("upload_file"),
    FRAME_SWITCH("frame_switch"),
    FRAME_MAIN("frame_main"),
    DIALOG_HANDLE("dialog_handle"),
    CAPTURE_NETWORK("capture_network"),
    WAIT_FOR_NETWORK("wait_for_network"),
    LLM_CALL("llm_call"),
    HTTP_REQUEST("http_request"),
    SEND_NOTIFICATION("send_notification"),
    LLM_AGENT("llm_agent"),
    ;

    private final String value;

    StepType(String value) { this.value = value; }

    public String value() { return value; }

    public static StepType fromValue(String value) {
        for (StepType t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown step type: " + value);
    }
}

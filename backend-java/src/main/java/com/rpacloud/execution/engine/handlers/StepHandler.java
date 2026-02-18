package com.rpacloud.execution.engine.handlers;

import java.util.Map;

import com.microsoft.playwright.Page;
import com.rpacloud.execution.engine.StepType;

/**
 * Strategy interface for step execution.
 * Each implementation handles one or more StepTypes.
 */
public interface StepHandler {

    StepType[] supportedTypes();

    HandlerResult execute(Page page, Map<String, Object> params, Map<String, Object> variables)
            throws Exception;
}

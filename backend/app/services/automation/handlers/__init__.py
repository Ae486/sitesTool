"""Step handlers for Playwright automation.

This module provides a registry of step handlers that can be used by PlaywrightExecutor.
Each handler follows the same signature: (page, params, variables, flow_id, index) -> dict

Usage:
    from app.services.automation.handlers import HANDLER_REGISTRY, StepType
    
    handler = HANDLER_REGISTRY.get(StepType.CLICK)
    result = await handler(page, params, variables, flow_id, index)
"""
from app.services.automation.dsl_parser import StepType

from .navigation import (
    handle_navigate,
    handle_new_tab,
    handle_switch_tab,
    handle_close_tab,
)
from .interactions import (
    handle_click,
    handle_input,
    handle_select,
    handle_checkbox,
    handle_scroll,
    handle_hover,
    handle_keyboard,
    handle_try_click,
)
from .wait import (
    handle_wait_for,
    handle_wait_time,
    handle_random_delay,
)
from .extract import (
    handle_extract,
    handle_extract_all,
    handle_screenshot,
)
from .assertions import (
    handle_assert_text,
    handle_assert_visible,
    handle_if_exists,
)
from .misc import (
    handle_set_variable,
    handle_eval_js,
)
from .network import (
    handle_capture_network,
    handle_wait_for_network,
)
from .base import resolve_variables, HandlerFunc, HandlerResult

# Handler registry mapping StepType to handler functions
# Note: Loop and control flow handlers are still in PlaywrightExecutor
# because they need access to internal methods (_execute_child_step)
HANDLER_REGISTRY: dict[StepType, HandlerFunc] = {
    StepType.NAVIGATE: handle_navigate,
    StepType.CLICK: handle_click,
    StepType.INPUT: handle_input,
    StepType.WAIT_FOR: handle_wait_for,
    StepType.WAIT_TIME: handle_wait_time,
    StepType.EXTRACT: handle_extract,
    # StepType.SCREENSHOT: handle_screenshot,  # Needs screenshot_dir from executor
    StepType.SELECT: handle_select,
    StepType.CHECKBOX: handle_checkbox,
    StepType.SCROLL: handle_scroll,
    StepType.HOVER: handle_hover,
    StepType.KEYBOARD: handle_keyboard,
    StepType.SET_VARIABLE: handle_set_variable,
    StepType.IF_EXISTS: handle_if_exists,
    StepType.ASSERT_TEXT: handle_assert_text,
    StepType.ASSERT_VISIBLE: handle_assert_visible,
    StepType.EXTRACT_ALL: handle_extract_all,
    StepType.RANDOM_DELAY: handle_random_delay,
    StepType.TRY_CLICK: handle_try_click,
    StepType.EVAL_JS: handle_eval_js,
    StepType.NEW_TAB: handle_new_tab,
    StepType.SWITCH_TAB: handle_switch_tab,
    StepType.CLOSE_TAB: handle_close_tab,
    StepType.CAPTURE_NETWORK: handle_capture_network,
    StepType.WAIT_FOR_NETWORK: handle_wait_for_network,
    # Control flow handlers stay in PlaywrightExecutor:
    # StepType.LOOP, StepType.LOOP_ARRAY, StepType.IF_ELSE
}

__all__ = [
    "HANDLER_REGISTRY",
    "HandlerFunc",
    "HandlerResult",
    "resolve_variables",
    # Navigation
    "handle_navigate",
    "handle_new_tab",
    "handle_switch_tab",
    "handle_close_tab",
    # Interactions
    "handle_click",
    "handle_input",
    "handle_select",
    "handle_checkbox",
    "handle_scroll",
    "handle_hover",
    "handle_keyboard",
    "handle_try_click",
    # Wait
    "handle_wait_for",
    "handle_wait_time",
    "handle_random_delay",
    # Extract
    "handle_extract",
    "handle_extract_all",
    "handle_screenshot",
    # Assertions
    "handle_assert_text",
    "handle_assert_visible",
    "handle_if_exists",
    # Misc
    "handle_set_variable",
    "handle_eval_js",
    # Network
    "handle_capture_network",
    "handle_wait_for_network",
]

"""Synchronous Playwright executor for Windows compatibility."""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any

from playwright.sync_api import sync_playwright, Browser, Page

from app.services.automation.dsl_parser import ParsedStep, StepType

logger = logging.getLogger(__name__)


@dataclass
class StepResult:
    """Result of executing a single step."""

    step_index: int
    step_type: str
    success: bool
    duration_ms: int
    message: str | None = None
    extracted_data: dict[str, Any] | None = None
    screenshot_path: str | None = None
    error: str | None = None


@dataclass
class ExecutionResult:
    """Result of executing an entire flow."""

    flow_id: int
    status: str  # "success", "failed", "partial"
    started_at: datetime
    completed_at: datetime
    total_duration_ms: int
    steps_executed: int
    steps_failed: int
    step_results: list[StepResult] = field(default_factory=list)
    variables: dict[str, Any] = field(default_factory=dict)
    error_message: str | None = None


class SyncPlaywrightExecutor:
    """Executes automation flows using synchronous Playwright (Windows compatible)."""

    def __init__(self, headless: bool = True, screenshot_dir: str = "data/screenshots"):
        self.headless = headless
        self.screenshot_dir = Path(screenshot_dir)
        self.screenshot_dir.mkdir(parents=True, exist_ok=True)

    def execute(self, flow_id: int, steps: list[ParsedStep]) -> ExecutionResult:
        """
        Execute a list of parsed steps synchronously.

        Args:
            flow_id: ID of the flow being executed
            steps: List of parsed steps to execute

        Returns:
            ExecutionResult with detailed execution information
        """
        started_at = datetime.utcnow()
        step_results = []
        variables = {}
        steps_failed = 0

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=self.headless)
            context = browser.new_context(
                viewport={"width": 1280, "height": 720},
                user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            )
            page = context.new_page()

            try:
                for idx, step in enumerate(steps):
                    logger.info(f"Executing step {idx + 1}/{len(steps)}: {step.type}")
                    step_start = datetime.utcnow()

                    try:
                        result = self._execute_step(page, step, idx, variables, flow_id)
                        step_results.append(result)

                        if result.extracted_data:
                            variables.update(result.extracted_data)

                        if not result.success:
                            steps_failed += 1
                            logger.warning(f"Step {idx + 1} failed: {result.error}")

                    except Exception as e:
                        logger.error(f"Step {idx + 1} error: {e}", exc_info=True)
                        step_duration = int(
                            (datetime.utcnow() - step_start).total_seconds() * 1000
                        )
                        step_results.append(
                            StepResult(
                                step_index=idx,
                                step_type=step.type.value,
                                success=False,
                                duration_ms=step_duration,
                                error=str(e),
                            )
                        )
                        steps_failed += 1

            finally:
                browser.close()

        completed_at = datetime.utcnow()
        total_duration = int((completed_at - started_at).total_seconds() * 1000)

        status = "success" if steps_failed == 0 else "failed"
        if 0 < steps_failed < len(steps):
            status = "partial"

        return ExecutionResult(
            flow_id=flow_id,
            status=status,
            started_at=started_at,
            completed_at=completed_at,
            total_duration_ms=total_duration,
            steps_executed=len(steps),
            steps_failed=steps_failed,
            step_results=step_results,
            variables=variables,
            error_message=None if steps_failed == 0 else f"{steps_failed} steps failed",
        )

    def _execute_step(
        self,
        page: Page,
        step: ParsedStep,
        index: int,
        variables: dict[str, Any],
        flow_id: int,
    ) -> StepResult:
        """Execute a single step."""
        start_time = datetime.utcnow()

        handlers = {
            StepType.NAVIGATE: self._handle_navigate,
            StepType.CLICK: self._handle_click,
            StepType.INPUT: self._handle_input,
            StepType.WAIT_FOR: self._handle_wait_for,
            StepType.WAIT_TIME: self._handle_wait_time,
            StepType.EXTRACT: self._handle_extract,
            StepType.SCREENSHOT: self._handle_screenshot,
            StepType.SELECT: self._handle_select,
            StepType.CHECKBOX: self._handle_checkbox,
            StepType.SCROLL: self._handle_scroll,
        }

        handler = handlers.get(step.type)
        if not handler:
            raise ValueError(f"No handler for step type: {step.type}")

        try:
            result_data = handler(page, step.params, variables, flow_id, index)
            duration = int((datetime.utcnow() - start_time).total_seconds() * 1000)

            return StepResult(
                step_index=index,
                step_type=step.type.value,
                success=True,
                duration_ms=duration,
                message=result_data.get("message"),
                extracted_data=result_data.get("extracted_data"),
                screenshot_path=result_data.get("screenshot_path"),
            )

        except Exception as e:
            duration = int((datetime.utcnow() - start_time).total_seconds() * 1000)
            return StepResult(
                step_index=index,
                step_type=step.type.value,
                success=False,
                duration_ms=duration,
                error=str(e),
            )

    def _handle_navigate(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle navigate step."""
        url = self._resolve_variables(params["url"], variables)
        wait_until = params.get("wait_until", "load")
        page.goto(url, wait_until=wait_until)
        return {"message": f"Navigated to {url}"}

    def _handle_click(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle click step."""
        selector = params["selector"]
        timeout = params.get("timeout", 5000)
        page.click(selector, timeout=timeout)
        return {"message": f"Clicked {selector}"}

    def _handle_input(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle input step."""
        selector = params["selector"]
        value = self._resolve_variables(params["value"], variables)
        clear = params.get("clear", True)

        if clear:
            page.fill(selector, "")
        page.fill(selector, str(value))
        return {"message": f"Input '{value}' into {selector}"}

    def _handle_wait_for(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle wait_for step."""
        selector = params["selector"]
        timeout = params.get("timeout", 10000)
        state = params.get("state", "visible")
        page.wait_for_selector(selector, timeout=timeout, state=state)
        return {"message": f"Waited for {selector}"}

    def _handle_wait_time(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle wait_time step."""
        import time

        duration = params["duration"]
        time.sleep(duration / 1000)
        return {"message": f"Waited {duration}ms"}

    def _handle_extract(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle extract step."""
        selector = params["selector"]
        variable = params["variable"]
        attribute = params.get("attribute")

        if attribute:
            value = page.get_attribute(selector, attribute)
        else:
            value = page.text_content(selector)

        return {
            "message": f"Extracted '{value}' from {selector}",
            "extracted_data": {variable: value},
        }

    def _handle_screenshot(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle screenshot step."""
        filename = params.get("path", f"flow_{flow_id}_step_{index}.png")
        full_page = params.get("full_page", False)

        screenshot_path = self.screenshot_dir / filename
        page.screenshot(path=str(screenshot_path), full_page=full_page)

        return {
            "message": f"Screenshot saved to {filename}",
            "screenshot_path": str(screenshot_path),
        }

    def _handle_select(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle select dropdown step."""
        selector = params["selector"]
        value = self._resolve_variables(params["value"], variables)
        page.select_option(selector, value)
        return {"message": f"Selected '{value}' in {selector}"}

    def _handle_checkbox(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle checkbox step."""
        selector = params["selector"]
        checked = params["checked"]

        if checked:
            page.check(selector)
            return {"message": f"Checked {selector}"}
        else:
            page.uncheck(selector)
            return {"message": f"Unchecked {selector}"}

    def _handle_scroll(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle scroll step."""
        selector = params.get("selector")
        x = params.get("x", 0)
        y = params.get("y", 0)

        if selector:
            page.locator(selector).scroll_into_view_if_needed()
            return {"message": f"Scrolled to {selector}"}
        else:
            page.evaluate(f"window.scrollBy({x}, {y})")
            return {"message": f"Scrolled by ({x}, {y})"}

    def _resolve_variables(self, value: str, variables: dict) -> str:
        """Resolve variable placeholders in string values."""
        if not isinstance(value, str):
            return value

        # Replace {{variable_name}} with actual values
        for var_name, var_value in variables.items():
            placeholder = f"{{{{{var_name}}}}}"
            if placeholder in value:
                value = value.replace(placeholder, str(var_value))

        return value


# Singleton instance
sync_executor = SyncPlaywrightExecutor(headless=False)  # Set to False for debugging

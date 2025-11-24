"""Playwright-based automation executor."""
from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

from playwright.async_api import Browser, Page, async_playwright

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
    description: str | None = None


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


class PlaywrightExecutor:
    """Executes automation flows using Playwright."""

    def __init__(
        self,
        headless: bool = True,
        browser_type: str = "chromium",
        browser_path: str | None = None,
        screenshot_dir: str = "data/screenshots",
        storage_state_dir: str = "data/storage_states",
    ):
        self.headless = headless
        self.browser_type = browser_type
        self.browser_path = browser_path
        self.screenshot_dir = Path(screenshot_dir)
        self.screenshot_dir.mkdir(parents=True, exist_ok=True)
        self.storage_state_dir = Path(storage_state_dir)
        self.storage_state_dir.mkdir(parents=True, exist_ok=True)

    async def execute(
        self,
        flow_id: int,
        steps: list[ParsedStep],
        use_cdp_mode: bool = False,
        cdp_port: int = 9222,
        cdp_user_data_dir: Optional[str] = None,
    ) -> ExecutionResult:
        """
        Execute a list of parsed steps.

        Args:
            flow_id: ID of the flow being executed
            steps: List of parsed steps to execute
            use_cdp_mode: Whether to use CDP mode (auto-starts browser with user profile)
            cdp_port: CDP debug port (default 9222)
            cdp_user_data_dir: Custom user data directory (uses default if not specified)

        Returns:
            ExecutionResult with detailed execution information
        """
        started_at = datetime.utcnow()
        step_results = []
        variables = {}
        steps_failed = 0

        async with async_playwright() as p:
            # Select browser based on type
            if self.browser_type == "chromium":
                browser_launcher = p.chromium
            elif self.browser_type == "firefox":
                browser_launcher = p.firefox
            elif self.browser_type == "chrome":
                browser_launcher = p.chromium
            elif self.browser_type == "edge":
                browser_launcher = p.chromium
            else:  # custom
                browser_launcher = p.chromium
            
            browser = None
            context = None
            page = None
            
            # MODE 1: CDP Mode (auto-start browser with copied profile if needed)
            browser_manager = None
            if use_cdp_mode:
                from app.services.automation.browser_launcher import get_browser_manager, is_cdp_ready
                
                logger.info(f"🎯 CDP Mode enabled")
                logger.info(f"   Port: {cdp_port}")
                logger.info(f"   Headless: {self.headless}")
                logger.info(f"   Custom user_data_dir: {cdp_user_data_dir or '(使用默认复制配置)'}")
                
                # Check if browser is already running
                if is_cdp_ready(cdp_port):
                    logger.warning("=" * 70)
                    logger.warning(f"⚠️  检测到浏览器已在端口 {cdp_port} 运行")
                    logger.warning("⚠️  将直接连接到现有浏览器（不启动新的）")
                    logger.warning("⚠️  headless 设置将被忽略")
                    logger.warning("⚠️  截图大小取决于现有浏览器窗口大小")
                    logger.warning("")
                    logger.warning("💡 如需启动新浏览器：")
                    logger.warning(f"   1. 关闭端口 {cdp_port} 上的浏览器进程")
                    logger.warning(f"   2. 或在前端使用不同的CDP端口（如9223）")
                    logger.warning("=" * 70)
                else:
                    logger.info(f"📌 Browser not running, auto-starting...")
                    logger.info(f"   Will use copied browser configuration")
                    logger.info(f"   Headless mode: {self.headless}")
                    
                    # Auto-start browser with copied/dedicated profile
                    browser_manager = get_browser_manager()
                    success = browser_manager.start_browser(
                        browser_type=self.browser_type,
                        port=cdp_port,
                        custom_path=self.browser_path,
                        user_data_dir=cdp_user_data_dir,  # Uses copied profile if None
                        headless=self.headless,  # Important: CDP supports headless!
                    )
                    
                    if not success:
                        raise RuntimeError(
                            f"CDP模式：无法启动浏览器\n"
                            f"浏览器类型：{self.browser_type}\n"
                            f"CDP端口：{cdp_port}\n"
                            f"请检查浏览器是否已安装"
                        )
                
                # Connect to browser via CDP
                try:
                    cdp_endpoint = f"http://localhost:{cdp_port}"
                    
                    # Final verification that CDP is ready
                    if not is_cdp_ready(cdp_port):
                        logger.warning("CDP not ready yet, waiting additional 5s...")
                        import asyncio
                        await asyncio.sleep(5)
                        if not is_cdp_ready(cdp_port):
                            raise RuntimeError(f"CDP interface on port {cdp_port} is not responding")
                    
                    logger.info(f"Connecting to CDP endpoint: {cdp_endpoint}")
                    
                    # Increased timeout for auto-started browser
                    browser = await browser_launcher.connect_over_cdp(
                        endpoint_url=cdp_endpoint,
                        timeout=60000  # 60 seconds timeout for auto-started browser
                    )
                    logger.info("CDP connection established")
                    
                    # Use default context (with all user's logins)
                    context = browser.contexts[0] if browser.contexts else await browser.new_context()
                    
                    # Use existing page or create new one
                    if context.pages:
                        page = context.pages[0]
                    else:
                        page = await context.new_page()
                    
                    logger.info(f"Successfully connected via CDP, using existing context with {len(context.pages)} pages")
                    
                except Exception as e:
                    logger.error(f"Failed to connect via CDP: {e}")
                    # Clean up auto-started browser if connection failed
                    if browser_manager:
                        browser_manager.stop_browser()
                    raise RuntimeError(
                        f"无法连接到浏览器（CDP端口{cdp_port}）。\n"
                        f"错误详情：{str(e)}"
                    )
            
            # MODE 2: Regular browser (fresh instance)
            else:
                # Launch options
                launch_options = {"headless": self.headless}
                
                # Use channel for Chrome/Edge (only if not custom)
                if self.browser_type == "chrome":
                    launch_options["channel"] = "chrome"
                elif self.browser_type == "edge":
                    launch_options["channel"] = "msedge"
                elif self.browser_type == "custom" and self.browser_path:
                    launch_options["executable_path"] = self.browser_path
                
                browser = await browser_launcher.launch(**launch_options)
                
                # Prepare context options
                context_options = {
                    "viewport": {"width": 1280, "height": 720},
                    "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                }
                
                context = await browser.new_context(**context_options)
                page = await context.new_page()
            
            # Ensure we have a page
            if not page:
                page = await context.new_page()

            try:
                for idx, step in enumerate(steps):
                    logger.info(f"Executing step {idx + 1}/{len(steps)}: {step.type}")
                    step_start = datetime.utcnow()

                    try:
                        result = await self._execute_step(
                            page, step, idx, variables, flow_id
                        )
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
                                description=step.description,
                            )
                        )
                        steps_failed += 1

            finally:
                # Cleanup: Close browser and context
                logger.info("=" * 70)
                logger.info("🧹 Cleanup: Closing browser and context...")
                
                try:
                    # Always close context first (if exists)
                    if context:
                        logger.info("   Closing context...")
                        await context.close()
                        logger.info("   ✅ Context closed")
                except Exception as e:
                    logger.warning(f"   ⚠️  Error closing context: {e}")
                
                try:
                    # Always close browser connection (disconnect from CDP or close regular browser)
                    if browser:
                        logger.info("   Closing browser connection...")
                        await browser.close()
                        logger.info("   ✅ Browser connection closed")
                except Exception as e:
                    logger.warning(f"   ⚠️  Error closing browser: {e}")
                
                # Inform user about CDP mode behavior
                if use_cdp_mode:
                    logger.info("ℹ️  CDP Mode: Browser process kept running with your profile")
                    logger.info("   💡 To close: Stop the browser manually or use different CDP port")
                    # If we started the browser, optionally stop it
                    if browser_manager:
                        logger.info("   🔧 We started this browser. Keeping it running for next use.")
                else:
                    logger.info("✅ Non-CDP Mode: Browser closed")
                
                logger.info("=" * 70)

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

    async def _execute_step(
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
            result_data = await handler(page, step.params, variables, flow_id, index)
            duration = int((datetime.utcnow() - start_time).total_seconds() * 1000)

            return StepResult(
                step_index=index,
                step_type=step.type.value,
                success=True,
                duration_ms=duration,
                message=result_data.get("message"),
                extracted_data=result_data.get("extracted_data"),
                screenshot_path=result_data.get("screenshot_path"),
                description=step.description,
            )

        except Exception as e:
            import traceback

            duration = int((datetime.utcnow() - start_time).total_seconds() * 1000)

            # Capture error screenshot
            error_screenshot_path = None
            try:
                error_filename = f"error_flow_{flow_id}_step_{index}_{int(start_time.timestamp())}.png"
                error_screenshot_path = self.screenshot_dir / error_filename
                await page.screenshot(path=str(error_screenshot_path), full_page=True)
                logger.info(f"Error screenshot saved: {error_screenshot_path}")
            except Exception as screenshot_error:
                logger.warning(f"Failed to capture error screenshot: {screenshot_error}")

            # Classify error type
            error_type = self._classify_error(e)

            # Get detailed error message
            error_detail = await self._format_error_detail(e, step, page)

            return StepResult(
                step_index=index,
                step_type=step.type.value,
                success=False,
                duration_ms=duration,
                error=f"[{error_type}] {error_detail}",
                screenshot_path=str(error_screenshot_path) if error_screenshot_path else None,
                description=step.description,
            )

    async def _handle_navigate(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle navigate step."""
        url = self._resolve_variables(params["url"], variables)
        wait_until = params.get("wait_until", "load")
        await page.goto(url, wait_until=wait_until)
        return {"message": f"Navigated to {url}"}

    async def _handle_click(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle click step."""
        selector = params["selector"]
        timeout = params.get("timeout", 5000)
        await page.click(selector, timeout=timeout)
        return {"message": f"Clicked {selector}"}

    async def _handle_input(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle input step."""
        selector = params["selector"]
        value = self._resolve_variables(params["value"], variables)
        clear = params.get("clear", True)

        if clear:
            await page.fill(selector, "")
        await page.fill(selector, str(value))
        return {"message": f"Input '{value}' into {selector}"}

    async def _handle_wait_for(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle wait_for step."""
        selector = params["selector"]
        timeout = params.get("timeout", 10000)
        state = params.get("state", "visible")
        await page.wait_for_selector(selector, timeout=timeout, state=state)
        return {"message": f"Waited for {selector}"}

    async def _handle_wait_time(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle wait_time step."""
        duration = params["duration"]
        await asyncio.sleep(duration / 1000)
        return {"message": f"Waited {duration}ms"}

    async def _handle_extract(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle extract step."""
        selector = params["selector"]
        variable = params["variable"]
        attribute = params.get("attribute")

        if attribute:
            value = await page.get_attribute(selector, attribute)
        else:
            value = await page.text_content(selector)

        return {
            "message": f"Extracted '{value}' from {selector}",
            "extracted_data": {variable: value},
        }

    async def _handle_screenshot(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle screenshot step."""
        filename = params.get("path", f"flow_{flow_id}_step_{index}.png")
        full_page = params.get("full_page", False)

        screenshot_path = self.screenshot_dir / filename
        await page.screenshot(path=str(screenshot_path), full_page=full_page)

        return {
            "message": f"Screenshot saved to {filename}",
            "screenshot_path": str(screenshot_path),
        }

    async def _handle_select(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle select dropdown step."""
        selector = params["selector"]
        value = self._resolve_variables(params["value"], variables)
        await page.select_option(selector, value)
        return {"message": f"Selected '{value}' in {selector}"}

    async def _handle_checkbox(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle checkbox step."""
        selector = params["selector"]
        checked = params["checked"]

        if checked:
            await page.check(selector)
            return {"message": f"Checked {selector}"}
        else:
            await page.uncheck(selector)
            return {"message": f"Unchecked {selector}"}

    async def _handle_scroll(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle scroll step."""
        selector = params.get("selector")
        x = params.get("x", 0)
        y = params.get("y", 0)

        if selector:
            await page.locator(selector).scroll_into_view_if_needed()
            return {"message": f"Scrolled to {selector}"}
        else:
            await page.evaluate(f"window.scrollBy({x}, {y})")
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

    def _classify_error(self, error: Exception) -> str:
        """Classify error type for better diagnostics."""
        error_str = str(error).lower()
        error_type = type(error).__name__

        if "timeout" in error_str or "TimeoutError" in error_type:
            return "TIMEOUT"
        elif "selector" in error_str or "element" in error_str or "locator" in error_str:
            return "ELEMENT_NOT_FOUND"
        elif "navigation" in error_str or "net::" in error_str:
            return "NAVIGATION_ERROR"
        elif "permission" in error_str or "denied" in error_str:
            return "PERMISSION_ERROR"
        else:
            return "EXECUTION_ERROR"

    async def _format_error_detail(
        self, error: Exception, step: ParsedStep, page: Page
    ) -> str:
        """Format detailed error message with context."""
        details = []
        details.append(str(error))

        # Add current URL
        try:
            current_url = page.url
            details.append(f"URL: {current_url}")
        except:
            pass

        # Add selector info if applicable
        if "selector" in step.params:
            selector = step.params["selector"]
            details.append(f"Selector: {selector}")

            # Check if selector exists
            try:
                count = await page.locator(selector).count()
                if count == 0:
                    details.append("⚠️ Element not found")
                    # Suggest similar selectors
                    try:
                        page_content = await page.content()
                        if selector.startswith("#"):
                            details.append("💡 Tip: Check if element ID is correct")
                        elif selector.startswith("."):
                            details.append("💡 Tip: Check if CSS class exists")
                    except:
                        pass
                else:
                    details.append(f"✓ Found {count} element(s)")
            except:
                pass

        # Add step description if available
        if step.description:
            details.append(f"Step: {step.description}")

        return " | ".join(details)


# Singleton instance
executor = PlaywrightExecutor(headless=False)  # Set to False for debugging

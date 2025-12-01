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
from app.services.automation.error_types import classify_error, ErrorType
from app.services.automation.cloudflare_handler import CloudflareHandler

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
        cf_protection: bool = True,  # Enable Cloudflare auto-handling
    ):
        self.headless = headless
        self.browser_type = browser_type
        self.browser_path = browser_path
        self.screenshot_dir = Path(screenshot_dir)
        self.screenshot_dir.mkdir(parents=True, exist_ok=True)
        self.storage_state_dir = Path(storage_state_dir)
        self.storage_state_dir.mkdir(parents=True, exist_ok=True)
        
        # Cloudflare challenge handler
        self.cf_handler = CloudflareHandler(
            enabled=cf_protection,
            check_probability=0.3,  # 30% random check between steps
            max_wait_time=45000,    # 45 seconds max wait
            check_after_navigate=True,  # Always check after navigation
        )

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
                
                # Prepare context options with anti-detection measures
                context_options = {
                    "viewport": {"width": 1920, "height": 1080},
                    "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "locale": "zh-CN",
                    "timezone_id": "Asia/Shanghai",
                    # Reduce bot detection
                    "java_script_enabled": True,
                    "bypass_csp": False,
                    "ignore_https_errors": True,
                }

                context = await browser.new_context(**context_options)
                
                # Anti-detection: Override navigator properties
                await context.add_init_script("""
                    // Override webdriver detection
                    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                    
                    // Override plugins
                    Object.defineProperty(navigator, 'plugins', {
                        get: () => [1, 2, 3, 4, 5]
                    });
                    
                    // Override languages
                    Object.defineProperty(navigator, 'languages', {
                        get: () => ['zh-CN', 'zh', 'en']
                    });
                    
                    // Override permissions
                    const originalQuery = window.navigator.permissions.query;
                    window.navigator.permissions.query = (parameters) => (
                        parameters.name === 'notifications' ?
                            Promise.resolve({ state: Notification.permission }) :
                            originalQuery(parameters)
                    );
                    
                    // Chrome runtime mock
                    window.chrome = { runtime: {} };
                    
                    // Override hardware concurrency
                    Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });
                    
                    // Override device memory
                    Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });
                """)
                page = await context.new_page()
            
            # Ensure we have a page
            if not page:
                page = await context.new_page()

            try:
                for idx, step in enumerate(steps):
                    logger.info(f"Executing step {idx + 1}/{len(steps)}: {step.type}")
                    step_start = datetime.utcnow()

                    # Pre-step CF check (random probability)
                    if await self.cf_handler.should_check(after_navigate=False):
                        cf_result = await self.cf_handler.check_and_handle(page)
                        if cf_result["detected"]:
                            logger.info(f"🛡️ Pre-step CF check: {cf_result['type']} handled={cf_result['handled']}")

                    try:
                        result = await self._execute_step(
                            page, step, idx, variables, flow_id
                        )
                        step_results.append(result)

                        if result.extracted_data:
                            variables.update(result.extracted_data)

                        # Post-navigate CF check (always after navigation)
                        if result.success and step.type == StepType.NAVIGATE:
                            cf_result = await self.cf_handler.check_and_handle(page)
                            if cf_result["detected"]:
                                logger.info(f"🛡️ Post-navigate CF: {cf_result['type']} handled={cf_result['handled']} ({cf_result['duration_ms']}ms)")
                                if not cf_result["handled"]:
                                    logger.warning("⚠️ CF challenge not resolved, continuing anyway...")

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
        context=None,
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
            StepType.HOVER: self._handle_hover,
            StepType.KEYBOARD: self._handle_keyboard,
            StepType.SET_VARIABLE: self._handle_set_variable,
            StepType.IF_EXISTS: self._handle_if_exists,
            StepType.ASSERT_TEXT: self._handle_assert_text,
            StepType.ASSERT_VISIBLE: self._handle_assert_visible,
            StepType.EXTRACT_ALL: self._handle_extract_all,
            StepType.RANDOM_DELAY: self._handle_random_delay,
            StepType.TRY_CLICK: self._handle_try_click,
            StepType.EVAL_JS: self._handle_eval_js,
            StepType.NEW_TAB: self._handle_new_tab,
            StepType.SWITCH_TAB: self._handle_switch_tab,
            StepType.CLOSE_TAB: self._handle_close_tab,
            StepType.LOOP: self._handle_loop,
            StepType.LOOP_ARRAY: self._handle_loop_array,
            StepType.IF_ELSE: self._handle_if_else,
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

    async def _handle_hover(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle hover step."""
        selector = params["selector"]
        await page.hover(selector)
        return {"message": f"Hovered over {selector}"}

    async def _handle_keyboard(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle keyboard step."""
        key = params["key"]
        await page.keyboard.press(key)
        return {"message": f"Pressed key: {key}"}

    async def _handle_set_variable(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle set_variable step."""
        variable = params["variable"]
        value = self._resolve_variables(params["value"], variables)
        return {
            "message": f"Set variable {variable} = {value}",
            "extracted_data": {variable: value},
        }

    async def _handle_if_exists(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle if_exists step - check if element exists."""
        selector = params["selector"]
        variable = params["variable"]
        timeout = params.get("timeout", 3000)

        try:
            await page.wait_for_selector(selector, timeout=timeout, state="attached")
            exists = True
        except Exception:
            exists = False

        return {
            "message": f"Element {selector} exists: {exists}",
            "extracted_data": {variable: exists},
        }

    async def _handle_assert_text(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle assert_text step - verify element contains text."""
        selector = params["selector"]
        expected = self._resolve_variables(params["expected"], variables)

        actual_text = await page.text_content(selector)
        if actual_text is None or expected not in actual_text:
            raise AssertionError(
                f"断言失败：元素 {selector} 不包含文本 '{expected}'。实际文本: '{actual_text}'"
            )

        return {"message": f"Assertion passed: '{expected}' found in {selector}"}

    async def _handle_assert_visible(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle assert_visible step - verify element is visible."""
        selector = params["selector"]
        timeout = params.get("timeout", 5000)

        await page.wait_for_selector(selector, timeout=timeout, state="visible")
        return {"message": f"Assertion passed: {selector} is visible"}

    async def _handle_extract_all(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle extract_all step - extract all matching elements."""
        selector = params["selector"]
        variable = params["variable"]
        attribute = params.get("attribute")

        elements = page.locator(selector)
        count = await elements.count()

        values = []
        for i in range(count):
            el = elements.nth(i)
            if attribute:
                val = await el.get_attribute(attribute)
            else:
                val = await el.text_content()
            if val:
                values.append(val.strip())

        return {
            "message": f"Extracted {len(values)} items from {selector}",
            "extracted_data": {variable: values},
        }

    async def _handle_random_delay(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle random_delay step."""
        import random

        min_ms = params["min"]
        max_ms = params["max"]
        delay = random.randint(min_ms, max_ms)
        await asyncio.sleep(delay / 1000)
        return {"message": f"Random delay: {delay}ms (range: {min_ms}-{max_ms})"}

    async def _handle_try_click(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle try_click step - click if element exists, skip otherwise."""
        selector = params["selector"]
        timeout = params.get("timeout", 3000)

        try:
            await page.wait_for_selector(selector, timeout=timeout, state="visible")
            await page.click(selector)
            return {"message": f"Clicked {selector}"}
        except Exception:
            return {"message": f"Skipped click: {selector} not found"}

    async def _handle_eval_js(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle eval_js step - execute JavaScript in page context."""
        script = self._resolve_variables(params["script"], variables)
        variable = params.get("variable")

        result = await page.evaluate(script)

        if variable:
            return {
                "message": f"Executed JS, result stored in {variable}",
                "extracted_data": {variable: result},
            }
        return {"message": f"Executed JS, result: {result}"}

    async def _handle_new_tab(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle new_tab step - open URL in new tab."""
        url = self._resolve_variables(params["url"], variables)
        tab_variable = params.get("tab_variable")
        
        # Store reference to context from page
        context = page.context
        new_page = await context.new_page()
        await new_page.goto(url)
        
        # Store page index as reference
        pages = context.pages
        tab_index = len(pages) - 1
        
        result = {"message": f"Opened new tab ({tab_index}) with URL: {url}"}
        if tab_variable:
            result["extracted_data"] = {tab_variable: tab_index}
        return result

    async def _handle_switch_tab(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle switch_tab step - switch to tab by index."""
        tab_index = int(params["index"])
        context = page.context
        pages = context.pages
        
        if tab_index < 0 or tab_index >= len(pages):
            raise ValueError(f"Tab index {tab_index} out of range (0-{len(pages)-1})")
        
        target_page = pages[tab_index]
        await target_page.bring_to_front()
        return {"message": f"Switched to tab {tab_index}"}

    async def _handle_close_tab(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle close_tab step - close current tab."""
        await page.close()
        return {"message": "Closed current tab"}

    async def _handle_loop(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle loop step - execute children N times."""
        times = int(params.get("times", 1))
        children = params.get("children", [])
        
        if not children:
            return {"message": f"Loop: no children to execute ({times} iterations)"}
        
        loop_results = []
        for i in range(times):
            logger.info(f"Loop iteration {i + 1}/{times}")
            for child in children:
                # Execute each child step
                child_result = await self._execute_child_step(page, child, variables, flow_id)
                loop_results.append(child_result)
                if child_result.get("extracted_data"):
                    variables.update(child_result["extracted_data"])
        
        return {
            "message": f"Loop completed: {times} iterations, {len(children)} steps each",
            "extracted_data": {"_loop_results": loop_results},
        }

    async def _handle_loop_array(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle loop_array step - iterate over array variable."""
        array_variable = params["array_variable"]
        item_variable = params["item_variable"]
        children = params.get("children", [])
        
        arr = variables.get(array_variable, [])
        if not isinstance(arr, list):
            arr = []
        
        if not children:
            return {"message": f"Loop array: no children ({len(arr)} items in {array_variable})"}
        
        loop_results = []
        for i, item in enumerate(arr):
            logger.info(f"Loop array iteration {i + 1}/{len(arr)}: {item_variable}={item}")
            variables[item_variable] = item
            for child in children:
                child_result = await self._execute_child_step(page, child, variables, flow_id)
                loop_results.append(child_result)
                if child_result.get("extracted_data"):
                    variables.update(child_result["extracted_data"])
        
        return {
            "message": f"Loop array completed: {len(arr)} iterations over {array_variable}",
            "extracted_data": {"_loop_array_results": loop_results},
        }

    async def _handle_if_else(
        self, page: Page, params: dict, variables: dict, flow_id: int, index: int
    ) -> dict:
        """Handle if_else step - conditional branching with multiple condition types."""
        condition_type = params.get("condition_type", "variable_truthy")
        condition_variable = params.get("condition_variable", "")
        condition_selector = params.get("condition_selector", "")
        condition_value = params.get("condition_value", "")
        timeout = params.get("timeout", 3000)
        children = params.get("children", [])
        else_children = params.get("else_children", [])
        
        # Evaluate condition based on type
        is_true = False
        condition_detail = ""
        
        try:
            if condition_type == "variable_truthy":
                # Variable is truthy (non-empty, non-zero, non-false)
                var_value = variables.get(condition_variable)
                is_true = bool(var_value) and var_value not in [0, "0", "false", "False", ""]
                condition_detail = f"变量 {condition_variable}={var_value}"
                
            elif condition_type == "variable_equals":
                # Variable equals specific value
                var_value = str(variables.get(condition_variable, ""))
                is_true = var_value == condition_value
                condition_detail = f"变量 {condition_variable}('{var_value}') == '{condition_value}'"
                
            elif condition_type == "variable_contains":
                # Variable contains specific text
                var_value = str(variables.get(condition_variable, ""))
                is_true = condition_value in var_value
                condition_detail = f"变量 {condition_variable}('{var_value}') 包含 '{condition_value}'"
                
            elif condition_type == "variable_greater":
                # Variable greater than number
                var_value = float(variables.get(condition_variable, 0))
                compare_value = float(condition_value)
                is_true = var_value > compare_value
                condition_detail = f"变量 {condition_variable}({var_value}) > {compare_value}"
                
            elif condition_type == "variable_less":
                # Variable less than number
                var_value = float(variables.get(condition_variable, 0))
                compare_value = float(condition_value)
                is_true = var_value < compare_value
                condition_detail = f"变量 {condition_variable}({var_value}) < {compare_value}"
                
            elif condition_type == "element_exists":
                # Element exists in page
                try:
                    element = await page.wait_for_selector(condition_selector, timeout=timeout, state="attached")
                    is_true = element is not None
                except Exception:
                    is_true = False
                condition_detail = f"元素 {condition_selector} 存在"
                
            elif condition_type == "element_visible":
                # Element is visible
                try:
                    element = await page.wait_for_selector(condition_selector, timeout=timeout, state="visible")
                    is_true = element is not None
                except Exception:
                    is_true = False
                condition_detail = f"元素 {condition_selector} 可见"
                
            elif condition_type == "element_text_equals":
                # Element text equals value
                try:
                    element = await page.wait_for_selector(condition_selector, timeout=timeout)
                    if element:
                        text = await element.text_content() or ""
                        is_true = text.strip() == condition_value
                        condition_detail = f"元素文本('{text.strip()}') == '{condition_value}'"
                    else:
                        is_true = False
                        condition_detail = f"元素 {condition_selector} 不存在"
                except Exception:
                    is_true = False
                    condition_detail = f"元素 {condition_selector} 检测失败"
                    
            elif condition_type == "element_text_contains":
                # Element text contains value
                try:
                    element = await page.wait_for_selector(condition_selector, timeout=timeout)
                    if element:
                        text = await element.text_content() or ""
                        is_true = condition_value in text
                        condition_detail = f"元素文本('{text[:50]}...') 包含 '{condition_value}'"
                    else:
                        is_true = False
                        condition_detail = f"元素 {condition_selector} 不存在"
                except Exception:
                    is_true = False
                    condition_detail = f"元素 {condition_selector} 检测失败"
            else:
                # Unknown condition type, default to false
                condition_detail = f"未知条件类型: {condition_type}"
                
        except Exception as e:
            logger.warning(f"Condition evaluation error: {e}")
            condition_detail = f"条件求值错误: {e}"
        
        branch_name = "then" if is_true else "else"
        target_children = children if is_true else else_children
        
        logger.info(f"If-else: [{condition_type}] {condition_detail} -> {is_true} -> {branch_name}")
        
        if not target_children:
            return {
                "message": f"If-else: {condition_detail} = {is_true}, {branch_name} 分支为空",
                "extracted_data": {"_condition_result": is_true, "_condition_detail": condition_detail},
            }
        
        branch_results = []
        for child in target_children:
            child_result = await self._execute_child_step(page, child, variables, flow_id)
            branch_results.append(child_result)
            if child_result.get("extracted_data"):
                variables.update(child_result["extracted_data"])
        
        return {
            "message": f"If-else: {condition_detail} = {is_true}, 执行 {branch_name} ({len(target_children)} 步)",
            "extracted_data": {
                "_condition_result": is_true,
                "_condition_detail": condition_detail,
                "_if_else_branch": branch_name,
                "_if_else_results": branch_results,
            },
        }

    async def _execute_child_step(
        self, page: Page, step_data: dict, variables: dict, flow_id: int
    ) -> dict:
        """Execute a child step from nested DSL structure."""
        from app.services.automation.dsl_parser import ParsedStep, StepType
        
        step_type_str = step_data.get("type", "")
        if step_type_str not in [e.value for e in StepType]:
            return {"success": False, "error": f"Unknown step type: {step_type_str}"}
        
        step_type = StepType(step_type_str)
        params = {k: v for k, v in step_data.items() if k not in ["type", "description"]}
        
        # Get the appropriate handler
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
            StepType.HOVER: self._handle_hover,
            StepType.KEYBOARD: self._handle_keyboard,
            StepType.SET_VARIABLE: self._handle_set_variable,
            StepType.IF_EXISTS: self._handle_if_exists,
            StepType.ASSERT_TEXT: self._handle_assert_text,
            StepType.ASSERT_VISIBLE: self._handle_assert_visible,
            StepType.EXTRACT_ALL: self._handle_extract_all,
            StepType.RANDOM_DELAY: self._handle_random_delay,
            StepType.TRY_CLICK: self._handle_try_click,
            StepType.EVAL_JS: self._handle_eval_js,
            StepType.NEW_TAB: self._handle_new_tab,
            StepType.SWITCH_TAB: self._handle_switch_tab,
            StepType.CLOSE_TAB: self._handle_close_tab,
            StepType.LOOP: self._handle_loop,
            StepType.LOOP_ARRAY: self._handle_loop_array,
            StepType.IF_ELSE: self._handle_if_else,
        }
        
        handler = handlers.get(step_type)
        if not handler:
            return {"success": False, "error": f"No handler for: {step_type_str}"}
        
        try:
            result = await handler(page, params, variables, flow_id, 0)
            return {"success": True, **result}
        except Exception as e:
            logger.error(f"Child step error ({step_type_str}): {e}")
            return {"success": False, "error": str(e)}

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
        """Classify error type for better diagnostics using the error_types module."""
        error_type = classify_error(error)
        return error_type.value

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

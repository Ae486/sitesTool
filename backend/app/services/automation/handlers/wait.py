"""Wait-related step handlers."""
import asyncio
import random
from typing import Any
from patchright.async_api import Page


async def handle_wait_for(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle wait_for step."""
    selector = params["selector"]
    timeout = params.get("timeout", 10000)
    state = params.get("state", "visible")
    await page.wait_for_selector(selector, timeout=timeout, state=state)
    return {"message": f"Waited for {selector}"}


async def handle_wait_time(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle wait_time step."""
    duration = params["duration"]
    await asyncio.sleep(duration / 1000)
    return {"message": f"Waited {duration}ms"}


async def handle_random_delay(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle random_delay step."""
    min_ms = params["min"]
    max_ms = params["max"]
    delay = random.randint(min_ms, max_ms)
    await asyncio.sleep(delay / 1000)
    return {"message": f"Random delay: {delay}ms (range: {min_ms}-{max_ms})"}

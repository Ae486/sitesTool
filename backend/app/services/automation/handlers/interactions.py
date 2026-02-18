"""Interaction-related step handlers (click, input, hover, etc.)."""
from typing import Any
from patchright.async_api import Page

from .base import resolve_variables


async def handle_click(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle click step."""
    selector = params["selector"]
    timeout = params.get("timeout", 5000)
    await page.click(selector, timeout=timeout)
    return {"message": f"Clicked {selector}"}


async def handle_input(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle input step."""
    selector = params["selector"]
    value = resolve_variables(params["value"], variables)
    clear = params.get("clear", True)

    if clear:
        await page.fill(selector, "")
    await page.fill(selector, str(value))
    return {"message": f"Input '{value}' into {selector}"}


async def handle_select(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle select dropdown step."""
    selector = params["selector"]
    value = resolve_variables(params["value"], variables)
    await page.select_option(selector, value)
    return {"message": f"Selected '{value}' in {selector}"}


async def handle_checkbox(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle checkbox step."""
    selector = params["selector"]
    checked = params["checked"]

    if checked:
        await page.check(selector)
        return {"message": f"Checked {selector}"}
    else:
        await page.uncheck(selector)
        return {"message": f"Unchecked {selector}"}


async def handle_scroll(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
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


async def handle_hover(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle hover step."""
    selector = params["selector"]
    await page.hover(selector)
    return {"message": f"Hovered over {selector}"}


async def handle_keyboard(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle keyboard step."""
    key = params["key"]
    await page.keyboard.press(key)
    return {"message": f"Pressed key: {key}"}


async def handle_try_click(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle try_click step - click if element exists, skip otherwise."""
    selector = params["selector"]
    timeout = params.get("timeout", 3000)

    try:
        await page.wait_for_selector(selector, timeout=timeout, state="visible")
        await page.click(selector)
        return {"message": f"Clicked {selector}"}
    except Exception:
        return {"message": f"Skipped click: {selector} not found"}

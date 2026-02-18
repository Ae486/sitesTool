"""Assertion step handlers."""
from typing import Any
from patchright.async_api import Page

from .base import resolve_variables


async def handle_assert_text(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle assert_text step - verify element contains text."""
    selector = params["selector"]
    expected = resolve_variables(params["expected"], variables)

    actual_text = await page.text_content(selector)
    if actual_text is None or expected not in actual_text:
        raise AssertionError(
            f"断言失败：元素 {selector} 不包含文本 '{expected}'。实际文本: '{actual_text}'"
        )

    return {"message": f"Assertion passed: '{expected}' found in {selector}"}


async def handle_assert_visible(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle assert_visible step - verify element is visible."""
    selector = params["selector"]
    timeout = params.get("timeout", 5000)

    await page.wait_for_selector(selector, timeout=timeout, state="visible")
    return {"message": f"Assertion passed: {selector} is visible"}


async def handle_if_exists(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
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

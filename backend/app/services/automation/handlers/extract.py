"""Extract and screenshot step handlers."""
from pathlib import Path
from typing import Any
from patchright.async_api import Page

from .base import resolve_variables


async def handle_extract(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
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


async def handle_extract_all(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
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


async def handle_screenshot(
    page: Page, 
    params: dict, 
    variables: dict, 
    flow_id: int, 
    index: int,
    screenshot_dir: Path | None = None
) -> dict[str, Any]:
    """Handle screenshot step.
    
    Note: screenshot_dir should be passed from executor context.
    """
    filename = params.get("path", f"flow_{flow_id}_step_{index}.png")
    full_page = params.get("full_page", False)

    if screenshot_dir:
        screenshot_path = screenshot_dir / filename
    else:
        screenshot_path = Path("data/screenshots") / filename
        screenshot_path.parent.mkdir(parents=True, exist_ok=True)
    
    await page.screenshot(path=str(screenshot_path), full_page=full_page)

    return {
        "message": f"Screenshot saved to {filename}",
        "screenshot_path": str(screenshot_path),
    }

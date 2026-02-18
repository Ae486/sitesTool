"""Navigation-related step handlers."""
from typing import Any
from patchright.async_api import Page

from .base import resolve_variables


async def handle_navigate(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle navigate step."""
    url = resolve_variables(params["url"], variables)
    wait_until = params.get("wait_until", "load")
    await page.goto(url, wait_until=wait_until)
    return {"message": f"Navigated to {url}"}


async def handle_new_tab(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle new_tab step - open URL in new tab."""
    url = resolve_variables(params["url"], variables)
    tab_variable = params.get("tab_variable")
    
    context = page.context
    new_page = await context.new_page()
    await new_page.goto(url)
    
    pages = context.pages
    tab_index = len(pages) - 1
    
    result: dict[str, Any] = {"message": f"Opened new tab ({tab_index}) with URL: {url}"}
    if tab_variable:
        result["extracted_data"] = {tab_variable: tab_index}
    return result


async def handle_switch_tab(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle switch_tab step - switch to tab by index."""
    tab_index = int(params["index"])
    context = page.context
    pages = context.pages
    
    if tab_index < 0 or tab_index >= len(pages):
        raise ValueError(f"Tab index {tab_index} out of range (0-{len(pages)-1})")
    
    target_page = pages[tab_index]
    await target_page.bring_to_front()
    return {"message": f"Switched to tab {tab_index}"}


async def handle_close_tab(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle close_tab step - close current tab."""
    await page.close()
    return {"message": "Closed current tab"}

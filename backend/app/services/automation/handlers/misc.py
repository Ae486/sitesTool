"""Miscellaneous step handlers (variables, JS eval, etc.)."""
from typing import Any
from patchright.async_api import Page

from .base import resolve_variables


async def handle_set_variable(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle set_variable step."""
    variable = params["variable"]
    value = resolve_variables(params["value"], variables)
    return {
        "message": f"Set variable {variable} = {value}",
        "extracted_data": {variable: value},
    }


async def handle_eval_js(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Handle eval_js step - execute JavaScript in page context."""
    script = resolve_variables(params["script"], variables)
    variable = params.get("variable")

    result = await page.evaluate(script)

    if variable:
        return {
            "message": f"Executed JS, result stored in {variable}",
            "extracted_data": {variable: result},
        }
    return {"message": f"Executed JS, result: {result}"}

"""Network interception step handlers."""
import re
from typing import Any

from patchright.async_api import Page

from .base import resolve_variables


async def handle_capture_network(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Register a response listener that captures matching network responses into variables.

    This is non-blocking: it registers the listener and returns immediately.
    Captured data is written to variables as responses arrive during subsequent steps.
    """
    url_pattern = resolve_variables(params["url_pattern"], variables)
    save_to = params["save_to"]
    pattern = re.compile(url_pattern)

    async def on_response(response):
        if pattern.search(response.url):
            try:
                body = await response.text()
            except Exception:
                body = ""
            variables[save_to] = body
            variables[save_to + "_url"] = response.url
            variables[save_to + "_status"] = response.status

    page.on("response", on_response)

    return {
        "message": f"Network capture registered for pattern: {url_pattern}",
        "extracted_data": {save_to: ""},
    }


async def handle_wait_for_network(
    page: Page, params: dict, variables: dict, flow_id: int, index: int
) -> dict[str, Any]:
    """Block until a network response matching the URL pattern arrives."""
    url_pattern = resolve_variables(params["url_pattern"], variables)
    save_to = params.get("save_to")
    timeout = params.get("timeout", 30000)
    pattern = re.compile(url_pattern)

    response = await page.wait_for_event(
        "response",
        predicate=lambda r: pattern.search(r.url) is not None,
        timeout=timeout,
    )

    extracted: dict[str, Any] = {}
    if save_to:
        try:
            body = await response.text()
        except Exception:
            body = ""
        extracted[save_to] = body
        extracted[save_to + "_url"] = response.url
        extracted[save_to + "_status"] = response.status

    return {
        "message": f"Captured response from {response.url} (status={response.status})",
        "extracted_data": extracted if extracted else None,
    }

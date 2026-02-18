"""Base types and utilities for step handlers."""
from typing import Any, Callable, Awaitable, TYPE_CHECKING
from dataclasses import dataclass

from app.services.automation.variable_resolver import resolve_variables as _resolve_variables

if TYPE_CHECKING:
    from patchright.async_api import Page

# Handler signature type
HandlerFunc = Callable[
    ["Page", dict, dict, int, int],  # page, params, variables, flow_id, index
    Awaitable[dict[str, Any]]
]


@dataclass
class HandlerResult:
    """Standard result from a step handler."""
    message: str
    extracted_data: dict[str, Any] | None = None
    screenshot_path: str | None = None


def resolve_variables(template: str, variables: dict[str, Any]) -> str:
    """Replace placeholders with variable values."""
    return _resolve_variables(template, variables, stringify_non_str=True)

"""Variable placeholder resolver for automation steps."""

from __future__ import annotations

from typing import Any, Mapping


def resolve_variables(
    value: Any,
    variables: Mapping[str, Any],
    *,
    stringify_non_str: bool = False,
) -> Any:
    """Resolve variable placeholders in a value.

    Supported placeholder styles:
    - {{var}}
    - ${var}

    Args:
        value: Any value. Only strings are processed for placeholders.
        variables: Mapping of variable name to value.
        stringify_non_str: If True, non-string values are converted to str.

    Returns:
        Resolved value, preserving the input type unless stringify_non_str=True.
    """

    if not isinstance(value, str):
        return str(value) if stringify_non_str else value

    result = value
    for var_name, var_value in variables.items():
        # Keep behavior stable across code paths by supporting both syntaxes
        result = result.replace(f"{{{{{var_name}}}}}", str(var_value))
        result = result.replace(f"${{{var_name}}}", str(var_value))

    return result

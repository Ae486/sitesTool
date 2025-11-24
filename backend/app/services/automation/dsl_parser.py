"""DSL Parser for automation flows."""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

REPO_ROOT = Path(__file__).resolve().parents[4]
DSL_SCHEMA_PATH = REPO_ROOT / "frontend" / "src" / "constants" / "dslSchema.json"

try:
    with DSL_SCHEMA_PATH.open("r", encoding="utf-8") as schema_file:
        STEP_DEFINITIONS: dict[str, Any] = json.load(schema_file)
except FileNotFoundError:  # pragma: no cover - misconfiguration safeguard
    logger.error("DSL schema file not found at %s", DSL_SCHEMA_PATH)
    STEP_DEFINITIONS = {}

StepType = Enum(
    "StepType",
    {name.upper(): name for name in STEP_DEFINITIONS.keys()},
    type=str,
)

REQUIRED_FIELDS: dict[str, list[str]] = {
    step: [field["name"] for field in config.get("fields", []) if field.get("required")]
    for step, config in STEP_DEFINITIONS.items()
}


@dataclass
class ParsedStep:
    """Parsed automation step with validated parameters."""

    type: StepType
    params: dict[str, Any]
    description: str | None = None

    def __post_init__(self):
        """Validate step parameters based on type."""
        self._validate_required_fields()
        if self.type == StepType.WAIT_TIME:
            self._validate_wait_time()
        if self.type == StepType.EXTRACT:
            self._validate_extract()
        if self.type == StepType.CHECKBOX:
            self._validate_checkbox()
        if self.type == StepType.SELECT:
            self._validate_select()
        if self.type == StepType.SCROLL:
            self._validate_scroll()

    def _validate_required_fields(self) -> None:
        required = REQUIRED_FIELDS.get(self.type.value, [])
        for field in required:
            value = self.params.get(field)
            if value is None:
                raise ValueError(f"{self.type.value} step requires '{field}' parameter")
            if isinstance(value, str) and not value.strip():
                raise ValueError(f"{self.type.value} step requires '{field}' parameter")

    def _validate_extract(self) -> None:
        selector = self.params.get("selector")
        variable = self.params.get("variable")
        if selector is None or (isinstance(selector, str) and not selector.strip()):
            raise ValueError("extract step requires 'selector' parameter")
        if variable is None or (isinstance(variable, str) and not variable.strip()):
            raise ValueError("extract step requires 'variable' parameter")

    def _validate_screenshot(self):
        # Screenshot has optional params, no required validation
        pass

    def _validate_select(self) -> None:
        selector = self.params.get("selector")
        value = self.params.get("value")
        if selector is None or (isinstance(selector, str) and not selector.strip()):
            raise ValueError("select step requires 'selector' parameter")
        if value is None or (isinstance(value, str) and not value.strip()):
            raise ValueError("select step requires 'value' parameter")

    def _validate_checkbox(self) -> None:
        if "selector" not in self.params:
            raise ValueError("checkbox step requires 'selector' parameter")
        if "checked" not in self.params:
            raise ValueError("checkbox step requires 'checked' parameter")

    def _validate_scroll(self) -> None:
        selector = self.params.get("selector")
        x_coord = self.params.get("x")
        y_coord = self.params.get("y")
        if not has_any_value(selector, x_coord, y_coord):
            raise ValueError("scroll step requires selector or x/y parameter")

    def _validate_wait_time(self) -> None:
        duration = self.params.get("duration")
        if not isinstance(duration, (int, float)):
            raise ValueError("wait_time step requires numeric 'duration'")
        if duration <= 0:
            raise ValueError("wait_time duration must be greater than zero")


class DSLParser:
    """Parser for automation DSL JSON."""

    def parse(self, dsl_json: str) -> list[ParsedStep]:
        """
        Parse DSL JSON string into list of validated steps.

        Args:
            dsl_json: JSON string containing automation steps

        Returns:
            List of ParsedStep objects

        Raises:
            ValueError: If DSL is invalid
        """
        try:
            dsl_data = json.loads(dsl_json)
        except json.JSONDecodeError as e:
            raise ValueError(f"Invalid JSON: {e}")

        if not isinstance(dsl_data, dict):
            raise ValueError("DSL must be a JSON object")

        if "steps" not in dsl_data:
            raise ValueError("DSL must contain 'steps' array")

        steps = dsl_data["steps"]
        if not isinstance(steps, list):
            raise ValueError("'steps' must be an array")

        parsed_steps = []
        for idx, step in enumerate(steps):
            try:
                parsed_step = self._parse_step(step)
                parsed_steps.append(parsed_step)
            except Exception as e:
                raise ValueError(f"Error parsing step {idx + 1}: {e}")

        logger.info(f"Successfully parsed {len(parsed_steps)} steps")
        return parsed_steps

    def _parse_step(self, step: dict) -> ParsedStep:
        """Parse a single step."""
        if not isinstance(step, dict):
            raise ValueError("Step must be a JSON object")

        if "type" not in step:
            raise ValueError("Step must have 'type' field")

        step_type_str = step["type"]
        if step_type_str not in STEP_DEFINITIONS:
            raise ValueError(
                f"Unknown step type '{step_type_str}'. "
                f"Supported types: {list(STEP_DEFINITIONS.keys())}"
            )
        step_type = StepType(step_type_str)

        params = {k: v for k, v in step.items() if k not in ["type", "description"]}
        description = step.get("description")

        return ParsedStep(type=step_type, params=params, description=description)


# Singleton instance
parser = DSLParser()


def has_any_value(*values: Any) -> bool:
    """Utility to verify at least one meaningful value exists."""
    for value in values:
        if value is None:
            continue
        if isinstance(value, str) and not value.strip():
            continue
        return True
    return False

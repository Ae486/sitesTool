"""Utilities for parsing automation subprocess output.

Goal: improve result stability even when stdout/stderr contains noisy logs.
"""

from __future__ import annotations

import json
from typing import Any


def extract_json_payload(output: str) -> dict[str, Any] | None:
    """Extract a JSON object payload from process output.

    Strategy (best-effort):
    1) Try json.loads on whole output
    2) Try parsing line-by-line from bottom (common pattern: last line is JSON)
    3) Try parsing from last '{' occurrences (handles noisy prefixes)

    Returns:
        Parsed dict payload if found, otherwise None.
    """

    if not output:
        return None

    text = output.strip()
    if not text:
        return None

    # 1) Direct parse
    try:
        value = json.loads(text)
        if isinstance(value, dict):
            return value
    except Exception:
        pass

    # 2) Last-line parse (reverse)
    lines = [ln.strip() for ln in text.splitlines() if ln.strip()]
    for ln in reversed(lines):
        if not (ln.startswith("{") and ln.endswith("}")):
            continue
        try:
            value = json.loads(ln)
            if isinstance(value, dict):
                return value
        except Exception:
            continue

    # 3) Try from last '{' positions
    idx = text.rfind("{")
    while idx != -1:
        candidate = text[idx:].strip()
        try:
            value = json.loads(candidate)
            if isinstance(value, dict):
                return value
        except Exception:
            pass
        idx = text.rfind("{", 0, idx)

    return None

from __future__ import annotations

from typing import Any

from app.services.automation.process_output_parser import extract_json_payload


def compute_observability_fields(
    *,
    result_payload: str | None,
    error_types: list[str] | None,
    error_message: str | None,
) -> tuple[str | None, str | None, str | None]:
    payload: dict[str, Any] | None = None
    if result_payload:
        payload = extract_json_payload(result_payload)

    execution_id = None
    if payload and isinstance(payload, dict):
        raw = payload.get("execution_id")
        if isinstance(raw, str) and raw:
            execution_id = raw

    primary_error_type = None
    if error_types:
        for t in error_types:
            if isinstance(t, str) and t:
                primary_error_type = t
                break

    failed_step_summary = None
    step_results = None
    if payload and isinstance(payload, dict):
        step_results = payload.get("step_results")

    if isinstance(step_results, list):
        for step in step_results:
            if not isinstance(step, dict):
                continue
            if step.get("success") is True:
                continue
            idx = step.get("step_index")
            step_num = None
            if isinstance(idx, int):
                step_num = idx + 1
            step_type = step.get("step_type")
            if not isinstance(step_type, str):
                step_type = None
            description = step.get("description")
            if not isinstance(description, str):
                description = None
            err = step.get("error")
            if not isinstance(err, str):
                err = None

            tag = None
            err_main = None
            if err:
                err_main = err.split("\n")[0].strip()
                if err_main.startswith("[") and "]" in err_main:
                    tag = err_main.split("]", 1)[0].lstrip("[")
                    err_main = err_main.split("]", 1)[1].strip() or err_main

            if not primary_error_type and tag:
                primary_error_type = tag

            parts: list[str] = []
            if step_num is not None:
                parts.append(f"步骤 {step_num}")
            if description:
                parts.append(description)
            elif step_type:
                parts.append(step_type)
            if tag:
                parts.append(tag)
            if err_main:
                parts.append(err_main)

            if parts:
                failed_step_summary = " - ".join(parts[:4])
            break

    if not failed_step_summary and error_message:
        failed_step_summary = error_message.split("\n")[0].strip() or None

    return execution_id, primary_error_type, failed_step_summary

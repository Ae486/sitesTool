from app.services.automation.history_observability import compute_observability_fields


def test_compute_observability_fields_from_payload():
    payload = '{"execution_id":"abc","status":"failed","step_results":[{"step_index":0,"step_type":"click","success":false,"error":"[TIMEOUT] boom"}]}'
    execution_id, primary, summary = compute_observability_fields(
        result_payload=payload,
        error_types=[],
        error_message=None,
    )
    assert execution_id == "abc"
    assert primary == "TIMEOUT"
    assert summary and "步骤 1" in summary


def test_compute_observability_fields_fallback_error_types():
    payload = '{"execution_id":"abc","status":"failed"}'
    execution_id, primary, summary = compute_observability_fields(
        result_payload=payload,
        error_types=["MANUAL_STOP"],
        error_message="执行被手动停止",
    )
    assert execution_id == "abc"
    assert primary == "MANUAL_STOP"
    assert summary

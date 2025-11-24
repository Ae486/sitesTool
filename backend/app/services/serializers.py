import json

from app.models.automation import AutomationFlow
from app.models.checkin import CheckinHistory
from app.schemas.flow import AutomationFlowRead, CheckinHistoryRead


def flow_to_schema(flow: AutomationFlow) -> AutomationFlowRead:
    # Parse DSL JSON string to dict
    dsl = json.loads(flow.dsl) if isinstance(flow.dsl, str) else flow.dsl
    return AutomationFlowRead(
        id=flow.id,
        site_id=flow.site_id,
        name=flow.name,
        description=flow.description,
        cron_expression=flow.cron_expression,
        is_active=flow.is_active,
        headless=flow.headless,
        browser_type=flow.browser_type,
        browser_path=flow.browser_path,
        use_cdp_mode=flow.use_cdp_mode,
        cdp_port=flow.cdp_port,
        cdp_user_data_dir=flow.cdp_user_data_dir,
        dsl=dsl,
        last_status=flow.last_status,
        created_at=flow.created_at,
        updated_at=flow.updated_at,
    )


def history_to_schema(history: CheckinHistory) -> CheckinHistoryRead:
    return CheckinHistoryRead(
        id=history.id,
        flow_id=history.flow_id,
        status=history.status,
        started_at=history.started_at,
        finished_at=history.finished_at,
        duration_ms=history.duration_ms,
        log=history.log,
        result_payload=history.result_payload,
        error_message=history.error_message,
        screenshot_files=history.screenshot_files or [],
        error_types=history.error_types or [],
        created_at=history.created_at,
        updated_at=history.updated_at,
    )

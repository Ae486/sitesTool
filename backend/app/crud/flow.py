from typing import Sequence

from sqlmodel import Session, select

from app.models.automation import AutomationFlow
from app.schemas.flow import AutomationFlowCreate, AutomationFlowUpdate


def list_flows(session: Session, *, skip: int = 0, limit: int = 20) -> list[AutomationFlow]:
    statement = select(AutomationFlow).offset(skip).limit(limit)
    results: Sequence[AutomationFlow] = session.exec(statement).all()
    return list(results)


def get_flow(session: Session, flow_id: int) -> AutomationFlow | None:
    return session.get(AutomationFlow, flow_id)


def create_flow(session: Session, flow_in: AutomationFlowCreate) -> AutomationFlow:
    import json
    
    flow = AutomationFlow(
        site_id=flow_in.site_id,
        name=flow_in.name,
        description=flow_in.description,
        cron_expression=flow_in.cron_expression,
        is_active=flow_in.is_active,
        headless=flow_in.headless,
        browser_type=flow_in.browser_type,
        browser_path=flow_in.browser_path,
        use_cdp_mode=flow_in.use_cdp_mode,
        cdp_port=flow_in.cdp_port,
        cdp_user_data_dir=flow_in.cdp_user_data_dir,
        dsl=json.dumps(flow_in.dsl) if isinstance(flow_in.dsl, dict) else flow_in.dsl,
    )
    session.add(flow)
    session.commit()
    session.refresh(flow)
    return flow


def update_flow(session: Session, flow: AutomationFlow, flow_in: AutomationFlowUpdate) -> AutomationFlow:
    import json
    
    data = flow_in.model_dump(exclude_unset=True)
    dsl = data.pop("dsl", None)
    for field, value in data.items():
        setattr(flow, field, value)
    if dsl is not None:
        flow.dsl = json.dumps(dsl) if isinstance(dsl, dict) else dsl
    session.add(flow)
    session.commit()
    session.refresh(flow)
    return flow


def delete_flow(session: Session, flow: AutomationFlow) -> None:
    session.delete(flow)
    session.commit()

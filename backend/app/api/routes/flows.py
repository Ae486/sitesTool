from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlmodel import Session

from app.api import deps
from app.crud import flow as flow_crud
from app.crud import history as history_crud
from app.models.user import User
from app.schemas.flow import (
    AutomationFlowCreate,
    AutomationFlowRead,
    AutomationFlowUpdate,
    FlowListResponse,
    HistoryListResponse,
)
from app.services.automation.executor import executor
from app.services.serializers import flow_to_schema, history_to_schema

router = APIRouter()


@router.get("", response_model=FlowListResponse)
def list_flows(
    skip: int = Query(0, ge=0),
    limit: int = Query(20, ge=1, le=100),
    db: Session = Depends(deps.get_db),
) -> FlowListResponse:
    flows = flow_crud.list_flows(db, skip=skip, limit=limit)
    items = [flow_to_schema(flow) for flow in flows]
    return FlowListResponse(total=len(items), items=items)


@router.post("", response_model=AutomationFlowRead, status_code=status.HTTP_201_CREATED)
def create_flow(
    flow_in: AutomationFlowCreate,
    db: Session = Depends(deps.get_db),
    _: User = Depends(deps.get_current_active_user),
) -> AutomationFlowRead:
    flow = flow_crud.create_flow(db, flow_in)
    return flow_to_schema(flow)


def _get_flow_or_404(flow_id: int, db: Session) -> AutomationFlowRead:
    flow = flow_crud.get_flow(db, flow_id)
    if not flow:
        raise HTTPException(status_code=404, detail="Flow not found")
    return flow_to_schema(flow)


@router.get("/{flow_id}", response_model=AutomationFlowRead)
def get_flow(flow_id: int, db: Session = Depends(deps.get_db)) -> AutomationFlowRead:
    return _get_flow_or_404(flow_id, db)


@router.put("/{flow_id}", response_model=AutomationFlowRead)
def update_flow(
    flow_id: int,
    flow_in: AutomationFlowUpdate,
    db: Session = Depends(deps.get_db),
    _: User = Depends(deps.get_current_active_user),
) -> AutomationFlowRead:
    flow = flow_crud.get_flow(db, flow_id)
    if not flow:
        raise HTTPException(status_code=404, detail="Flow not found")
    updated = flow_crud.update_flow(db, flow, flow_in)
    return flow_to_schema(updated)


@router.delete("/{flow_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_flow(
    flow_id: int,
    db: Session = Depends(deps.get_db),
    _: User = Depends(deps.get_current_active_user),
) -> None:
    flow = flow_crud.get_flow(db, flow_id)
    if not flow:
        raise HTTPException(status_code=404, detail="Flow not found")
    flow_crud.delete_flow(db, flow)


@router.post("/{flow_id}/trigger")
def trigger_flow(
    flow_id: int,
    db: Session = Depends(deps.get_db),
    _: User = Depends(deps.get_current_active_user),
) -> dict[str, str | None]:
    flow = flow_crud.get_flow(db, flow_id)
    if not flow:
        raise HTTPException(status_code=404, detail="Flow not found")
    result = executor.trigger(flow)
    return {"status": result.status, "message": result.message}


@router.post("/{flow_id}/stop")
def stop_flow(
    flow_id: int,
    db: Session = Depends(deps.get_db),
    _: User = Depends(deps.get_current_active_user),
) -> dict[str, str | None]:
    flow = flow_crud.get_flow(db, flow_id)
    if not flow:
        raise HTTPException(status_code=404, detail="Flow not found")
    result = executor.stop(flow)
    return {"status": result.status, "message": result.message}


@router.get("/{flow_id}/status")
def get_flow_status(
    flow_id: int,
    db: Session = Depends(deps.get_db),
) -> dict[str, bool]:
    flow = flow_crud.get_flow(db, flow_id)
    if not flow:
        raise HTTPException(status_code=404, detail="Flow not found")
    is_running = executor.is_running(flow_id)
    return {"is_running": is_running}


@router.get("/running/list")
def list_running_flows(
    _: User = Depends(deps.get_current_active_user),
) -> dict[str, list[int]]:
    running_flows = executor.get_running_flows()
    return {"running_flows": running_flows}


@router.get("/{flow_id}/history", response_model=HistoryListResponse)
def get_history(
    flow_id: int,
    error_type: str | None = Query(default=None, description="Filter by error type code"),
    db: Session = Depends(deps.get_db),
) -> HistoryListResponse:
    flow = flow_crud.get_flow(db, flow_id)
    if not flow:
        raise HTTPException(status_code=404, detail="Flow not found")
    items = [
        history_to_schema(item)
        for item in history_crud.list_by_flow(db, flow_id, error_type=error_type)
    ]
    return HistoryListResponse(total=len(items), items=items)

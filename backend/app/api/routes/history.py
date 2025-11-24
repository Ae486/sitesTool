"""History API routes."""
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlmodel import Session

from app.api import deps
from app.crud import history as history_crud
from app.models.user import User
from app.schemas.flow import CheckinHistoryRead, HistoryListResponse
from app.services.serializers import history_to_schema

router = APIRouter()


@router.get("", response_model=HistoryListResponse)
def list_history(
    skip: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=100),
    error_type: str | None = Query(default=None, description="Filter by error type code"),
    db: Session = Depends(deps.get_db),
) -> HistoryListResponse:
    """List all execution history."""
    items = history_crud.list_all(db, skip=skip, limit=limit, error_type=error_type)
    total = history_crud.count_all(db, error_type=error_type)
    history_items = [history_to_schema(h) for h in items]
    return HistoryListResponse(total=total, items=history_items)


@router.get("/{history_id}", response_model=CheckinHistoryRead)
def get_history(
    history_id: int,
    db: Session = Depends(deps.get_db),
) -> CheckinHistoryRead:
    """Get a single history record."""
    history = history_crud.get_by_id(db, history_id)
    if not history:
        raise HTTPException(status_code=404, detail="History not found")
    return history_to_schema(history)


@router.delete("/{history_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_history(
    history_id: int,
    db: Session = Depends(deps.get_db),
    _: User = Depends(deps.get_current_active_user),
) -> None:
    """Delete a history record."""
    history = history_crud.get_by_id(db, history_id)
    if not history:
        raise HTTPException(status_code=404, detail="History not found")
    history_crud.delete_history(db, history)

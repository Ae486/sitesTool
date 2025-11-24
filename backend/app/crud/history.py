from typing import Optional, Sequence

from sqlmodel import Session, select

from app.models.checkin import CheckinHistory


def _apply_error_filter(statement, error_type: Optional[str]):
    if error_type:
        pattern = f'%"{error_type}"%'
        statement = statement.where(CheckinHistory.error_types.like(pattern))
    return statement


def list_all(
    session: Session, *, skip: int = 0, limit: int = 50, error_type: str | None = None
) -> list[CheckinHistory]:
    """List all history records."""
    statement = (
        select(CheckinHistory)
        .order_by(CheckinHistory.started_at.desc())
        .offset(skip)
        .limit(limit)
    )
    statement = _apply_error_filter(statement, error_type)
    results: Sequence[CheckinHistory] = session.exec(statement).all()
    return list(results)


def list_by_flow(
    session: Session,
    flow_id: int,
    *,
    skip: int = 0,
    limit: int = 20,
    error_type: str | None = None,
) -> list[CheckinHistory]:
    """List history records for a specific flow."""
    statement = (
        select(CheckinHistory)
        .where(CheckinHistory.flow_id == flow_id)
        .order_by(CheckinHistory.started_at.desc())
        .offset(skip)
        .limit(limit)
    )
    statement = _apply_error_filter(statement, error_type)
    results: Sequence[CheckinHistory] = session.exec(statement).all()
    return list(results)


def get_by_id(session: Session, history_id: int) -> CheckinHistory | None:
    """Get a single history record by ID."""
    return session.get(CheckinHistory, history_id)


def delete_history(session: Session, history: CheckinHistory) -> None:
    """Delete a history record."""
    session.delete(history)
    session.commit()


def count_all(session: Session, error_type: str | None = None) -> int:
    """Count total history records."""
    from sqlalchemy import func

    statement = select(func.count(CheckinHistory.id))
    statement = _apply_error_filter(statement, error_type)
    return session.exec(statement).one()

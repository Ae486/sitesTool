from datetime import datetime
from typing import TYPE_CHECKING, Optional

from sqlalchemy import JSON, Column, Index
from sqlmodel import Field, Relationship

from app.models.automation import FlowStatus
from app.models.base import IDModel, TimestampedModel

if TYPE_CHECKING:
    from app.models.automation import AutomationFlow


class CheckinHistory(IDModel, TimestampedModel, table=True):
    __tablename__ = "checkin_history"
    __table_args__ = (
        # 常用查询索引：按 flow_id 和 status 筛选
        Index("ix_checkin_history_flow_id_status", "flow_id", "status"),
        # 时间范围查询索引
        Index("ix_checkin_history_started_at", "started_at"),
        # 单独的 flow_id 索引（用于外键查询）
        Index("ix_checkin_history_flow_id", "flow_id"),
    )

    flow_id: int = Field(foreign_key="automation_flows.id", index=True)
    status: FlowStatus = Field(default=FlowStatus.IDLE)
    started_at: datetime = Field(default_factory=datetime.utcnow)
    finished_at: Optional[datetime] = None
    duration_ms: Optional[int] = None
    log: Optional[str] = Field(default=None, description="Plaintext log output")
    result_payload: Optional[str] = Field(default=None, description="JSON result data")
    error_message: Optional[str] = None
    screenshot_files: list[str] = Field(
        default_factory=list,
        sa_column=Column(JSON, nullable=False, server_default="[]"),
        description="JSON array of screenshot file names",
    )
    error_types: list[str] = Field(
        default_factory=list,
        sa_column=Column(JSON, nullable=False, server_default="[]"),
        description="JSON array of error type codes",
    )

    flow: "AutomationFlow" = Relationship(back_populates="checkins")

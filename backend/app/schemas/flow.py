from __future__ import annotations

from datetime import datetime
from typing import Any, Optional

from pydantic import BaseModel, Field
from sqlmodel import SQLModel

from app.models.automation import FlowStatus


class AutomationFlowBase(SQLModel):
    site_id: int
    name: str
    description: Optional[str] = None
    cron_expression: Optional[str] = None
    is_active: bool = True
    headless: bool = True
    browser_type: str = "chromium"
    browser_path: Optional[str] = None
    use_cdp_mode: bool = False
    cdp_port: int = 9222
    cdp_user_data_dir: Optional[str] = None
    dsl: dict[str, Any]


class AutomationFlowCreate(AutomationFlowBase):
    ...


class AutomationFlowUpdate(SQLModel):
    name: Optional[str] = None
    description: Optional[str] = None
    cron_expression: Optional[str] = None
    is_active: Optional[bool] = None
    headless: Optional[bool] = None
    browser_type: Optional[str] = None
    browser_path: Optional[str] = None
    use_cdp_mode: Optional[bool] = None
    cdp_port: Optional[int] = None
    cdp_user_data_dir: Optional[str] = None
    dsl: Optional[dict[str, Any]] = None


class AutomationFlowRead(AutomationFlowBase):
    id: int
    last_status: FlowStatus
    created_at: datetime
    updated_at: datetime


class CheckinHistoryRead(BaseModel):
    id: int
    flow_id: int
    status: FlowStatus
    started_at: datetime
    finished_at: Optional[datetime]
    duration_ms: Optional[int]
    log: Optional[str] = None
    result_payload: Optional[str] = None
    error_message: Optional[str]
    screenshot_files: list[str] = Field(default_factory=list)
    error_types: list[str] = Field(default_factory=list)
    created_at: datetime
    updated_at: datetime


class FlowListResponse(BaseModel):
    total: int
    items: list[AutomationFlowRead]


class HistoryListResponse(BaseModel):
    total: int
    items: list[CheckinHistoryRead]

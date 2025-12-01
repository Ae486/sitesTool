from enum import Enum
from typing import TYPE_CHECKING, List, Optional

from sqlmodel import Field, Relationship

from app.models.base import IDModel, TimestampedModel

if TYPE_CHECKING:
    from app.models.checkin import CheckinHistory
    from app.models.site import Site


class FlowStatus(str, Enum):
    IDLE = "idle"
    RUNNING = "running"
    SUCCESS = "success"
    FAILED = "failed"


class AutomationFlow(IDModel, TimestampedModel, table=True):
    __tablename__ = "automation_flows"

    site_id: int = Field(foreign_key="sites.id")
    name: str = Field(max_length=200)
    description: Optional[str] = None
    cron_expression: Optional[str] = Field(
        default=None, description="Optional APScheduler cron expression"
    )
    is_active: bool = Field(default=True)
    dsl: str = Field(description="JSON string describing actions")
    last_status: FlowStatus = Field(default=FlowStatus.IDLE)
    headless: bool = Field(default=True, description="Run browser in headless mode")
    browser_type: str = Field(
        default="chromium", description="Browser type: chromium, chrome, edge, firefox, custom"
    )
    browser_path: Optional[str] = Field(
        default=None, description="Custom browser executable path"
    )
    use_cdp_mode: bool = Field(
        default=False, description="Connect to running browser via CDP (uses all existing logins)"
    )
    cdp_port: int = Field(
        default=9222, description="CDP debug port"
    )
    cdp_user_data_dir: Optional[str] = Field(
        default=None, description="Browser user data directory (uses default profile if not specified)"
    )

    site: "Site" = Relationship(back_populates="automation_flows")
    checkins: List["CheckinHistory"] = Relationship(
        back_populates="flow",
        sa_relationship_kwargs={"cascade": "all, delete-orphan"},
    )

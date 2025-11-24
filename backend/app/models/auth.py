from enum import Enum
from typing import TYPE_CHECKING, Optional

from sqlmodel import Field, Relationship

from app.models.base import IDModel, TimestampedModel

if TYPE_CHECKING:
    from app.models.site import Site


class AuthMethod(str, Enum):
    COOKIES = "cookies"
    CREDENTIALS = "credentials"
    SCRIPT = "script"


class AuthProfile(IDModel, TimestampedModel, table=True):
    __tablename__ = "auth_profiles"

    site_id: int = Field(foreign_key="sites.id")
    method: AuthMethod = Field(default=AuthMethod.COOKIES)
    username: Optional[str] = Field(default=None, max_length=200)
    password: Optional[str] = None
    cookies_json: Optional[str] = None
    script: Optional[str] = Field(
        default=None, description="Optional Python snippet to execute custom login"
    )
    notes: Optional[str] = None
    is_active: bool = Field(default=True)

    site: "Site" = Relationship(back_populates="auth_profile")

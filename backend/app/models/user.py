from typing import Optional

from sqlmodel import Field

from app.models.base import IDModel, TimestampedModel


class User(IDModel, TimestampedModel, table=True):
    __tablename__ = "users"

    email: str = Field(unique=True, index=True, max_length=255)
    hashed_password: str
    full_name: Optional[str] = None
    is_active: bool = Field(default=True)
    is_superuser: bool = Field(default=False)

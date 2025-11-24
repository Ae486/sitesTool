from __future__ import annotations

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, HttpUrl, field_validator
from sqlmodel import SQLModel


class CategoryBase(SQLModel):
    name: str
    description: Optional[str] = None


class CategoryRead(CategoryBase):
    id: int


class TagBase(SQLModel):
    name: str
    color: Optional[str] = None


class TagRead(TagBase):
    id: int


class TagCreate(TagBase):
    pass


class SiteBase(SQLModel):
    name: str
    url: HttpUrl
    description: Optional[str] = None
    category_id: Optional[int] = None
    tag_ids: list[int] = []
    is_active: bool = True
    sort_order: int = 0

    @field_validator("tag_ids", mode="before")
    @classmethod
    def ensure_list(cls, value):
        if value is None:
            return []
        return value


class SiteCreate(SiteBase):
    ...


class SiteUpdate(SQLModel):
    name: Optional[str] = None
    url: Optional[HttpUrl] = None
    description: Optional[str] = None
    category_id: Optional[int] = None
    tag_ids: Optional[list[int]] = None
    is_active: Optional[bool] = None
    sort_order: Optional[int] = None


class SiteRead(SiteBase):
    id: int
    created_at: datetime
    updated_at: datetime
    category: Optional[CategoryRead] = None
    tags: list[TagRead] = []


class SiteListResponse(BaseModel):
    total: int
    items: list[SiteRead]

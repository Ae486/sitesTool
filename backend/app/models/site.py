from typing import TYPE_CHECKING, List, Optional

from sqlmodel import Field, Relationship, SQLModel

from app.models.base import IDModel, TimestampedModel

if TYPE_CHECKING:
    from app.models.auth import AuthProfile
    from app.models.automation import AutomationFlow


class Category(IDModel, TimestampedModel, table=True):
    __tablename__ = "categories"

    name: str = Field(index=True, max_length=100)
    description: Optional[str] = None

    sites: List["Site"] = Relationship(back_populates="category")


class SiteTagLink(SQLModel, table=True):
    __tablename__ = "site_tag_links"

    site_id: int = Field(foreign_key="sites.id", primary_key=True)
    tag_id: int = Field(foreign_key="tags.id", primary_key=True)


class Tag(IDModel, TimestampedModel, table=True):
    __tablename__ = "tags"

    name: str = Field(index=True, max_length=100)
    color: Optional[str] = Field(default=None, description="Optional hex color code")

    sites: List["Site"] = Relationship(back_populates="tags", link_model=SiteTagLink)


class Site(IDModel, TimestampedModel, table=True):
    __tablename__ = "sites"

    name: str = Field(index=True, max_length=200)
    url: str = Field(max_length=1024)
    description: Optional[str] = None
    category_id: Optional[int] = Field(default=None, foreign_key="categories.id")
    sort_order: int = Field(default=0)
    is_active: bool = Field(default=True)

    category: Optional[Category] = Relationship(back_populates="sites")
    tags: List[Tag] = Relationship(back_populates="sites", link_model=SiteTagLink)
    auth_profile: Optional["AuthProfile"] = Relationship(back_populates="site")
    automation_flows: List["AutomationFlow"] = Relationship(back_populates="site")

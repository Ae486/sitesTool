from typing import Sequence

from sqlalchemy.orm import selectinload
from sqlmodel import Session, select

from app.models.site import Site, Tag
from app.schemas.site import SiteCreate, SiteUpdate


def list_sites(session: Session, *, skip: int = 0, limit: int = 20) -> list[Site]:
    statement = select(Site).options(selectinload(Site.tags)).offset(skip).limit(limit)
    results: Sequence[Site] = session.exec(statement).all()
    return list(results)


def get_site(session: Session, site_id: int) -> Site | None:
    statement = select(Site).where(Site.id == site_id).options(selectinload(Site.tags))
    return session.exec(statement).first()


def _resolve_tags(session: Session, tag_ids: list[int]) -> list[Tag]:
    if not tag_ids:
        return []
    statement = select(Tag).where(Tag.id.in_(tag_ids))
    return list(session.exec(statement).all())


def create_site(session: Session, site_in: SiteCreate) -> Site:
    site = Site(
        name=site_in.name,
        url=str(site_in.url),
        description=site_in.description,
        category_id=site_in.category_id,
        sort_order=site_in.sort_order,
        is_active=site_in.is_active,
    )
    session.add(site)
    session.flush()
    site.tags = _resolve_tags(session, site_in.tag_ids)
    session.commit()
    session.refresh(site)
    return get_site(session, site.id)


def update_site(session: Session, site: Site, site_in: SiteUpdate) -> Site:
    data = site_in.model_dump(exclude_unset=True)
    if "url" in data and data["url"] is not None:
        data["url"] = str(data["url"])

    tag_ids = data.pop("tag_ids", None)

    for field, value in data.items():
        setattr(site, field, value)

    if tag_ids is not None:
        site.tags = _resolve_tags(session, tag_ids)

    session.add(site)
    session.commit()
    session.refresh(site)
    return get_site(session, site.id)


def delete_site(session: Session, site: Site) -> None:
    session.delete(site)
    session.commit()

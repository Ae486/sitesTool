from typing import Sequence

from sqlmodel import Session, select, delete

from app.models.site import Category, Tag, SiteTagLink


def list_categories(session: Session) -> list[Category]:
    statement = select(Category).order_by(Category.name)
    results: Sequence[Category] = session.exec(statement).all()
    return list(results)


def list_tags(session: Session) -> list[Tag]:
    statement = select(Tag).order_by(Tag.name)
    results: Sequence[Tag] = session.exec(statement).all()
    return list(results)


def upsert_tag(session: Session, name: str, color: str | None = None) -> Tag:
    statement = select(Tag).where(Tag.name == name)
    tag = session.exec(statement).first()
    if tag:
        tag.color = color or tag.color
    else:
        tag = Tag(name=name, color=color)
        session.add(tag)
    session.commit()
    session.refresh(tag)
    return tag


def delete_tag(session: Session, tag_id: int) -> bool:
    tag = session.get(Tag, tag_id)
    if not tag:
        return False

    # 先删除关联关系
    session.exec(delete(SiteTagLink).where(SiteTagLink.tag_id == tag_id))

    # 再删除标签
    session.delete(tag)
    session.commit()
    return True

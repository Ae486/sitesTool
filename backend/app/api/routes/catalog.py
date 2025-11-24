from fastapi import APIRouter, Depends, HTTPException, status
from sqlmodel import Session

from app.api import deps
from app.crud import catalog as catalog_crud
from app.models.user import User
from app.schemas.site import CategoryRead, TagCreate, TagRead

router = APIRouter()


@router.get("/categories", response_model=list[CategoryRead])
def list_categories(db: Session = Depends(deps.get_db)) -> list[CategoryRead]:
    return catalog_crud.list_categories(db)


@router.get("/tags", response_model=list[TagRead])
def list_tags(db: Session = Depends(deps.get_db)) -> list[TagRead]:
    return catalog_crud.list_tags(db)


@router.post("/tags", response_model=TagRead, status_code=status.HTTP_201_CREATED)
def create_tag(
    tag: TagCreate,
    db: Session = Depends(deps.get_db),
    _: User = Depends(deps.get_current_active_user),
) -> TagRead:
    result = catalog_crud.upsert_tag(db, name=tag.name, color=tag.color)
    return TagRead(id=result.id, name=result.name, color=result.color)


@router.delete("/tags/{tag_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_tag(
    tag_id: int,
    db: Session = Depends(deps.get_db),
    _: User = Depends(deps.get_current_active_user),
) -> None:
    deleted = catalog_crud.delete_tag(db, tag_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Tag not found")

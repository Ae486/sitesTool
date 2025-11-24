from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlmodel import Session

from app.api import deps
from app.crud import site as site_crud
from app.models.user import User
from app.schemas.site import SiteCreate, SiteListResponse, SiteRead, SiteUpdate

router = APIRouter()


@router.get("", response_model=SiteListResponse)
def list_sites(
    skip: int = Query(0, ge=0),
    limit: int = Query(20, ge=1, le=100),
    db: Session = Depends(deps.get_db),
) -> SiteListResponse:
    items = site_crud.list_sites(db, skip=skip, limit=limit)
    return SiteListResponse(total=len(items), items=items)


@router.post("", response_model=SiteRead, status_code=status.HTTP_201_CREATED)
def create_site(
    site_in: SiteCreate,
    db: Session = Depends(deps.get_db),
    _: User = Depends(deps.get_current_active_user),
) -> SiteRead:
    return site_crud.create_site(db, site_in)


@router.get("/{site_id}", response_model=SiteRead)
def get_site(site_id: int, db: Session = Depends(deps.get_db)) -> SiteRead:
    site = site_crud.get_site(db, site_id)
    if not site:
        raise HTTPException(status_code=404, detail="Site not found")
    return site


@router.put("/{site_id}", response_model=SiteRead)
def update_site(
    site_id: int,
    site_in: SiteUpdate,
    db: Session = Depends(deps.get_db),
    _: User = Depends(deps.get_current_active_user),
) -> SiteRead:
    site = site_crud.get_site(db, site_id)
    if not site:
        raise HTTPException(status_code=404, detail="Site not found")
    return site_crud.update_site(db, site, site_in)


@router.delete("/{site_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_site(
    site_id: int,
    db: Session = Depends(deps.get_db),
    _: User = Depends(deps.get_current_active_user),
) -> None:
    site = site_crud.get_site(db, site_id)
    if not site:
        raise HTTPException(status_code=404, detail="Site not found")
    site_crud.delete_site(db, site)

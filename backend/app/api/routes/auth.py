from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from pydantic import BaseModel, EmailStr
from sqlalchemy import func, select
from sqlmodel import Session

from app.api import deps
from app.core.security import create_access_token, get_password_hash
from app.crud import user as crud_user
from app.models.user import User
from app.schemas.auth import Token, UserRead

router = APIRouter()


@router.post("/token", response_model=Token)
def login_access_token(
    db: Session = Depends(deps.get_db), form_data: OAuth2PasswordRequestForm = Depends()
) -> Token:
    user = crud_user.authenticate(db, email=form_data.username, password=form_data.password)
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Incorrect credentials")
    token = create_access_token(subject=user.email)
    return Token(access_token=token)


class BootstrapRequest(BaseModel):
    email: EmailStr
    password: str
    full_name: str | None = None


@router.post("/bootstrap", response_model=UserRead)
def bootstrap_admin(data: BootstrapRequest, db: Session = Depends(deps.get_db)) -> User:
    statement = select(func.count(User.id))
    existing = db.exec(statement).one()
    if existing and existing[0] > 0:
        raise HTTPException(status_code=400, detail="Admin already initialized")
    user = User(
        email=data.email,
        hashed_password=get_password_hash(data.password),
        full_name=data.full_name,
        is_superuser=True,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@router.get("/me", response_model=UserRead)
def read_current_user(current_user: User = Depends(deps.get_current_active_user)) -> User:
    return current_user

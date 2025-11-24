from typing import Generator
from datetime import datetime

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from sqlmodel import Session

from app.core.config import settings
from app.db.session import get_session
from app.models.user import User
from app.schemas.auth import TokenPayload
from app.crud.user import get_by_email

oauth2_scheme = OAuth2PasswordBearer(
    tokenUrl=f"{settings.api_prefix}/auth/token",
    auto_error=not settings.disable_auth  # 禁用认证时不自动抛出401错误
)


def get_db() -> Generator[Session, None, None]:
    yield from get_session()


def get_current_user(
    token: str | None = Depends(oauth2_scheme), db: Session = Depends(get_db)
) -> User:
    # 如果禁用认证，返回模拟管理员用户
    if settings.disable_auth:
        # 返回一个包含所有必需字段的模拟超级用户对象
        now = datetime.utcnow()
        return User(
            id=1,
            email="dev@localhost",
            full_name="开发模式用户",
            is_active=True,
            is_superuser=True,
            hashed_password="not-used",
            created_at=now,
            updated_at=now,
        )
    
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, settings.secret_key, algorithms=["HS256"])
        token_data = TokenPayload(**payload)
    except jwt.PyJWTError as exc:  # type: ignore[attr-defined]
        raise credentials_exception from exc
    if token_data.sub is None:
        raise credentials_exception
    user = get_by_email(db, token_data.sub)
    if user is None:
        raise credentials_exception
    return user


def get_current_active_user(current_user: User = Depends(get_current_user)) -> User:
    if not current_user.is_active:
        raise HTTPException(status_code=400, detail="Inactive user")
    return current_user

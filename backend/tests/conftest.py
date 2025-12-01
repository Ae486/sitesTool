"""Pytest configuration and fixtures."""
from typing import Generator

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlmodel import Session, SQLModel, create_engine
from sqlmodel.pool import StaticPool

from app.api.router import api_router
from app.api.deps import get_db


@pytest.fixture(name="engine")
def engine_fixture():
    """Create a test database engine."""
    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    SQLModel.metadata.create_all(engine)
    return engine


@pytest.fixture(name="session")
def session_fixture(engine) -> Generator[Session, None, None]:
    """Create a test database session."""
    with Session(engine) as session:
        yield session


@pytest.fixture(name="client")
def client_fixture(session: Session) -> Generator[TestClient, None, None]:
    """Create a minimal test app without static file mounts."""
    # Create a fresh app for testing (without static files that interfere)
    test_app = FastAPI()
    test_app.include_router(api_router, prefix="/api")
    
    @test_app.get("/healthz")
    def healthcheck():
        return {"status": "ok"}
    
    def get_db_override():
        yield session

    test_app.dependency_overrides[get_db] = get_db_override
    
    with TestClient(test_app) as client:
        yield client

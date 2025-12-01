"""Tests for sites API endpoints."""
import pytest
from fastapi.testclient import TestClient


def test_list_sites_empty(client: TestClient):
    """Test listing sites when database is empty."""
    response = client.get("/api/sites")
    assert response.status_code == 200
    data = response.json()
    assert data["total"] == 0
    assert data["items"] == []


def test_create_site_requires_auth(client: TestClient):
    """Test that creating a site requires authentication."""
    response = client.post(
        "/api/sites",
        json={"name": "Test Site", "url": "https://example.com"},
    )
    # Should return 401 without auth token
    assert response.status_code == 401


def test_healthcheck(client: TestClient):
    """Test the health check endpoint."""
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}

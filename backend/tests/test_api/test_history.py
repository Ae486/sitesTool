"""Tests for history API endpoints."""
import pytest
from fastapi.testclient import TestClient


def test_list_history_empty(client: TestClient):
    """Test listing history when database is empty."""
    response = client.get("/api/history")
    assert response.status_code == 200
    data = response.json()
    assert data["total"] == 0
    assert data["items"] == []


def test_get_history_not_found(client: TestClient):
    """Test getting a non-existent history record."""
    response = client.get("/api/history/999")
    assert response.status_code == 404
    assert response.json()["detail"] == "History not found"


def test_delete_history_requires_auth(client: TestClient):
    """Test that deleting history requires authentication."""
    response = client.delete("/api/history/1")
    assert response.status_code == 401

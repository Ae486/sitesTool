"""Tests for flows API endpoints."""
import pytest
from fastapi.testclient import TestClient


def test_list_flows_empty(client: TestClient):
    """Test listing flows when database is empty."""
    response = client.get("/api/flows")
    assert response.status_code == 200
    data = response.json()
    assert data["total"] == 0
    assert data["items"] == []


def test_get_flow_not_found(client: TestClient):
    """Test getting a non-existent flow."""
    response = client.get("/api/flows/999")
    assert response.status_code == 404
    assert response.json()["detail"] == "Flow not found"


def test_trigger_flow_requires_auth(client: TestClient):
    """Test that triggering a flow requires authentication."""
    response = client.post("/api/flows/1/trigger")
    assert response.status_code == 401

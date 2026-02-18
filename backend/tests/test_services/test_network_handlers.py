"""Tests for network interception step handlers."""
import asyncio
import sys
import types
from unittest.mock import AsyncMock, MagicMock

# Stub patchright before any handler imports
_patchright_stub = types.ModuleType("patchright")
_async_api_stub = types.ModuleType("patchright.async_api")
_sync_api_stub = types.ModuleType("patchright.sync_api")
_async_api_stub.Page = MagicMock()
_async_api_stub.Browser = MagicMock()
_async_api_stub.async_playwright = MagicMock()
_sync_api_stub.Page = MagicMock()
_sync_api_stub.Browser = MagicMock()
_sync_api_stub.sync_playwright = MagicMock()
sys.modules.setdefault("patchright", _patchright_stub)
sys.modules.setdefault("patchright.async_api", _async_api_stub)
sys.modules.setdefault("patchright.sync_api", _sync_api_stub)

from app.services.automation.handlers.network import (  # noqa: E402
    handle_capture_network,
    handle_wait_for_network,
)


def _run(coro):
    return asyncio.get_event_loop().run_until_complete(coro)


def _make_response(url: str, status: int, body: str):
    resp = AsyncMock()
    resp.url = url
    resp.status = status
    resp.text = AsyncMock(return_value=body)
    return resp


def _make_page():
    page = AsyncMock()
    page._response_listeners = []

    def on_response(event, callback):
        page._response_listeners.append(callback)

    page.on = MagicMock(side_effect=on_response)
    return page


# ── capture_network ──────────────────────────────────────────────


def test_capture_network_registers_listener():
    page = _make_page()
    params = {"url_pattern": ".*api/data.*", "save_to": "resp"}

    result = _run(handle_capture_network(page, params, {}, flow_id=1, index=0))

    page.on.assert_called_once()
    assert page.on.call_args[0][0] == "response"
    assert result["message"].startswith("Network capture registered")
    assert result["extracted_data"] == {"resp": ""}


def test_capture_network_callback_stores_matching_response():
    page = _make_page()
    params = {"url_pattern": ".*api/data.*", "save_to": "resp"}
    variables: dict = {}

    _run(handle_capture_network(page, params, variables, flow_id=1, index=0))

    callback = page._response_listeners[0]
    response = _make_response("https://example.com/api/data/123", 200, '{"ok":true}')
    _run(callback(response))

    assert variables["resp"] == '{"ok":true}'
    assert variables["resp_url"] == "https://example.com/api/data/123"
    assert variables["resp_status"] == 200


def test_capture_network_callback_ignores_non_matching():
    page = _make_page()
    params = {"url_pattern": ".*api/data.*", "save_to": "resp"}
    variables: dict = {}

    _run(handle_capture_network(page, params, variables, flow_id=1, index=0))

    callback = page._response_listeners[0]
    response = _make_response("https://example.com/static/logo.png", 200, "")
    _run(callback(response))

    assert "resp" not in variables


def test_capture_network_callback_handles_text_error():
    page = _make_page()
    params = {"url_pattern": ".*api.*", "save_to": "r"}
    variables: dict = {}

    _run(handle_capture_network(page, params, variables, flow_id=1, index=0))

    callback = page._response_listeners[0]
    response = _make_response("https://example.com/api/bin", 200, "")
    response.text = AsyncMock(side_effect=Exception("binary body"))
    _run(callback(response))

    assert variables["r"] == ""
    assert variables["r_url"] == "https://example.com/api/bin"


def test_capture_network_resolves_variables_in_pattern():
    page = _make_page()
    params = {"url_pattern": ".*{{host}}.*", "save_to": "resp"}
    variables = {"host": "example.com"}

    result = _run(handle_capture_network(page, params, variables, flow_id=1, index=0))

    assert ".*example.com.*" in result["message"]


# ── wait_for_network ─────────────────────────────────────────────


def test_wait_for_network_returns_captured_data():
    page = AsyncMock()
    response = _make_response("https://api.example.com/users", 200, '[{"id":1}]')
    page.wait_for_event = AsyncMock(return_value=response)

    params = {"url_pattern": ".*api.*users", "save_to": "users", "timeout": 5000}

    result = _run(handle_wait_for_network(page, params, {}, flow_id=1, index=0))

    page.wait_for_event.assert_called_once()
    call_kwargs = page.wait_for_event.call_args
    assert call_kwargs[0][0] == "response"
    assert call_kwargs[1]["timeout"] == 5000

    assert result["extracted_data"]["users"] == '[{"id":1}]'
    assert result["extracted_data"]["users_url"] == "https://api.example.com/users"
    assert result["extracted_data"]["users_status"] == 200


def test_wait_for_network_without_save_to():
    page = AsyncMock()
    response = _make_response("https://api.example.com/ping", 204, "")
    page.wait_for_event = AsyncMock(return_value=response)

    params = {"url_pattern": ".*ping"}

    result = _run(handle_wait_for_network(page, params, {}, flow_id=1, index=0))

    assert result["extracted_data"] is None
    assert "status=204" in result["message"]


def test_wait_for_network_default_timeout():
    page = AsyncMock()
    response = _make_response("https://x.com/api", 200, "ok")
    page.wait_for_event = AsyncMock(return_value=response)

    params = {"url_pattern": ".*api", "save_to": "d"}
    _run(handle_wait_for_network(page, params, {}, flow_id=1, index=0))

    assert page.wait_for_event.call_args[1]["timeout"] == 30000


def test_wait_for_network_text_error_fallback():
    page = AsyncMock()
    response = _make_response("https://x.com/api/bin", 200, "")
    response.text = AsyncMock(side_effect=Exception("binary"))
    page.wait_for_event = AsyncMock(return_value=response)

    params = {"url_pattern": ".*api", "save_to": "d"}
    result = _run(handle_wait_for_network(page, params, {}, flow_id=1, index=0))

    assert result["extracted_data"]["d"] == ""


def test_wait_for_network_predicate_uses_regex():
    page = AsyncMock()
    response = _make_response("https://cdn.example.com/v2/data?page=1", 200, "{}")
    page.wait_for_event = AsyncMock(return_value=response)

    params = {"url_pattern": r"v2/data\?page=\d+", "save_to": "d"}
    _run(handle_wait_for_network(page, params, {}, flow_id=1, index=0))

    predicate = page.wait_for_event.call_args[1]["predicate"]
    assert predicate(_make_response("https://cdn.example.com/v2/data?page=1", 200, ""))
    assert not predicate(_make_response("https://cdn.example.com/v1/other", 200, ""))

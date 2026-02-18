"""Tests for extracting JSON payload from noisy subprocess output."""

from app.services.automation.process_output_parser import extract_json_payload


def test_extract_json_payload_direct_json():
    payload = '{"status": "success", "x": 1}'
    assert extract_json_payload(payload) == {"status": "success", "x": 1}


def test_extract_json_payload_last_line_json():
    output = "log line 1\nlog line 2\n{\"status\": \"failed\", \"message\": \"boom\"}\n"
    assert extract_json_payload(output) == {"status": "failed", "message": "boom"}


def test_extract_json_payload_from_last_brace_block():
    output = "INFO something {not-json}\nmore logs\n{\"status\": \"success\", \"data\": {\"a\": 1}}"
    assert extract_json_payload(output) == {"status": "success", "data": {"a": 1}}

"""Tests for error type classification."""
import pytest

from app.services.automation.error_types import (
    ErrorType,
    classify_error,
    get_error_display_info,
)


class TestClassifyError:
    """Tests for classify_error function."""

    def test_classify_timeout_error(self):
        """Test classification of timeout errors."""
        assert classify_error("TimeoutError: Timeout 30000ms exceeded") == ErrorType.TIMEOUT
        assert classify_error("Operation timed out") == ErrorType.TIMEOUT

    def test_classify_element_not_found(self):
        """Test classification of element not found errors."""
        assert classify_error("No element found for selector #button") == ErrorType.ELEMENT_NOT_FOUND
        assert classify_error("Waiting for selector '#login'") == ErrorType.ELEMENT_NOT_FOUND
        assert classify_error("locator resolved to 0 elements") == ErrorType.ELEMENT_NOT_FOUND

    def test_classify_element_not_visible(self):
        """Test classification of element visibility errors."""
        assert classify_error("Element is not visible") == ErrorType.ELEMENT_NOT_VISIBLE

    def test_classify_element_not_interactable(self):
        """Test classification of interactability errors."""
        assert classify_error("Element is not interactable") == ErrorType.ELEMENT_NOT_INTERACTABLE
        assert classify_error("Element click intercepted by another element") == ErrorType.ELEMENT_NOT_INTERACTABLE

    def test_classify_navigation_error(self):
        """Test classification of navigation errors."""
        assert classify_error("Navigation to https://example.com failed") == ErrorType.NAVIGATION_ERROR
        # ERR_NAME_NOT_RESOLVED is actually a DNS error (more specific)
        assert classify_error("page.goto: net::ERR_NAME_NOT_RESOLVED") == ErrorType.DNS_ERROR

    def test_classify_network_error(self):
        """Test classification of network errors."""
        assert classify_error("net::ERR_CONNECTION_REFUSED") == ErrorType.NETWORK_ERROR
        assert classify_error("Failed to fetch resource") == ErrorType.NETWORK_ERROR

    def test_classify_ssl_error(self):
        """Test classification of SSL errors."""
        assert classify_error("SSL certificate problem") == ErrorType.SSL_ERROR
        assert classify_error("Certificate verification failed") == ErrorType.SSL_ERROR
        assert classify_error("net::ERR_CERT_AUTHORITY_INVALID") == ErrorType.SSL_ERROR

    def test_classify_dns_error(self):
        """Test classification of DNS errors."""
        assert classify_error("DNS lookup failed") == ErrorType.DNS_ERROR
        assert classify_error("getaddrinfo ENOTFOUND") == ErrorType.DNS_ERROR

    def test_classify_browser_crash(self):
        """Test classification of browser crash errors."""
        assert classify_error("Browser crashed unexpectedly") == ErrorType.BROWSER_CRASH
        assert classify_error("Browser disconnected") == ErrorType.BROWSER_CRASH

    def test_classify_browser_closed(self):
        """Test classification of browser closed errors."""
        assert classify_error("Target closed") == ErrorType.BROWSER_CLOSED
        assert classify_error("Page closed") == ErrorType.BROWSER_CLOSED
        assert classify_error("Context closed") == ErrorType.BROWSER_CLOSED

    def test_classify_cdp_error(self):
        """Test classification of CDP errors."""
        assert classify_error("CDP connection failed") == ErrorType.CDP_CONNECTION_ERROR
        assert classify_error("Failed to connect to DevTools") == ErrorType.CDP_CONNECTION_ERROR

    def test_classify_permission_error(self):
        """Test classification of permission errors."""
        assert classify_error("Permission denied") == ErrorType.PERMISSION_ERROR
        assert classify_error("Access denied to resource") == ErrorType.PERMISSION_ERROR

    def test_classify_manual_stop(self):
        """Test classification of manual stop."""
        assert classify_error("Manually stopped by user") == ErrorType.MANUAL_STOP
        assert classify_error("User cancelled execution") == ErrorType.MANUAL_STOP
        assert classify_error("手动停止") == ErrorType.MANUAL_STOP

    def test_classify_process_timeout(self):
        """Test classification of process timeout."""
        assert classify_error("Process timeout after 300s") == ErrorType.PROCESS_TIMEOUT
        assert classify_error("执行超时") == ErrorType.PROCESS_TIMEOUT

    def test_classify_validation_error(self):
        """Test classification of validation errors."""
        assert classify_error("Invalid selector syntax error") == ErrorType.SELECTOR_INVALID
        assert classify_error("JSONDecodeError: Invalid JSON") == ErrorType.DSL_PARSE_ERROR
        assert classify_error("ValueError: validation error") == ErrorType.VALIDATION_ERROR

    def test_classify_assertion_error(self):
        """Test classification of assertion errors."""
        assert classify_error("Assertion failed: expected value") == ErrorType.ASSERTION_FAILED

    def test_classify_unknown_error(self):
        """Test that unrecognized errors return UNKNOWN."""
        assert classify_error("Some completely random error message xyz123") == ErrorType.UNKNOWN

    def test_classify_exception_object(self):
        """Test classification with actual exception objects."""
        assert classify_error(TimeoutError("Operation timed out")) == ErrorType.TIMEOUT
        assert classify_error(ValueError("Invalid value")) == ErrorType.VALIDATION_ERROR
        assert classify_error(PermissionError("Access denied")) == ErrorType.PERMISSION_ERROR


class TestGetErrorDisplayInfo:
    """Tests for get_error_display_info function."""

    def test_get_display_info_timeout(self):
        """Test display info for timeout error."""
        info = get_error_display_info(ErrorType.TIMEOUT)
        assert info["label"] == "超时"
        assert "color" in info
        assert "description" in info

    def test_get_display_info_manual_stop(self):
        """Test display info for manual stop."""
        info = get_error_display_info(ErrorType.MANUAL_STOP)
        assert info["label"] == "手动停止"
        assert info["color"] == "blue"

    def test_get_display_info_unknown(self):
        """Test display info for unknown error."""
        info = get_error_display_info(ErrorType.UNKNOWN)
        assert info["label"] == "未知错误"
        assert info["color"] == "default"

    def test_all_error_types_have_display_info(self):
        """Test that all error types have display info defined."""
        for error_type in ErrorType:
            info = get_error_display_info(error_type)
            assert "label" in info
            assert "description" in info
            assert "color" in info

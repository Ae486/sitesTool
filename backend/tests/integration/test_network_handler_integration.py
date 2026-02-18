"""Integration tests for network handler — uses real Patchright browser + local HTTP server."""
import asyncio
import re

from app.services.automation.handlers.network import (
    handle_capture_network,
    handle_wait_for_network,
)


def _run(loop, coro):
    return loop.run_until_complete(coro)


class TestCaptureNetworkIntegration:

    def test_captures_real_network_response(self, browser, target_server, event_loop):
        """Navigate to a page that triggers fetch() → verify response captured in variables."""
        async def _test():
            page = await browser.new_page()
            try:
                variables = {}
                params = {"url_pattern": ".*/api/data.*", "save_to": "api_resp"}

                # Register listener
                await handle_capture_network(page, params, variables, flow_id=1, index=0)

                # Navigate to page that auto-fetches /api/data after 500ms
                await page.goto(f"{target_server}/page")
                await page.wait_for_timeout(2000)

                # Verify captured data
                assert "api_resp" in variables, f"Expected api_resp in variables, got: {list(variables.keys())}"
                assert '"items"' in variables["api_resp"]
                assert variables["api_resp_status"] == 200
                assert "/api/data" in variables["api_resp_url"]
            finally:
                await page.close()

        _run(event_loop, _test())

    def test_ignores_non_matching_urls(self, browser, target_server, event_loop):
        """Listener with strict pattern should not capture unrelated requests."""
        async def _test():
            page = await browser.new_page()
            try:
                variables = {}
                params = {"url_pattern": ".*/api/nonexistent.*", "save_to": "missed"}

                await handle_capture_network(page, params, variables, flow_id=1, index=0)
                await page.goto(f"{target_server}/page")
                await page.wait_for_timeout(2000)

                # Should NOT have captured anything
                assert "missed" not in variables
            finally:
                await page.close()

        _run(event_loop, _test())


class TestWaitForNetworkIntegration:

    def test_waits_and_captures_response(self, browser, target_server, event_loop):
        """wait_for_network blocks until matching response arrives."""
        async def _test():
            page = await browser.new_page()
            try:
                params = {
                    "url_pattern": ".*/api/data.*",
                    "save_to": "waited",
                    "timeout": 10000,
                }

                # Navigate first (page has JS that fetches /api/data after 500ms)
                await page.goto(f"{target_server}/page")

                # Wait for the network response
                result = await handle_wait_for_network(page, params, {}, flow_id=1, index=0)

                assert result["extracted_data"]["waited_status"] == 200
                assert "/api/data" in result["extracted_data"]["waited_url"]
                assert '"items"' in result["extracted_data"]["waited"]
                assert "status=200" in result["message"]
            finally:
                await page.close()

        _run(event_loop, _test())

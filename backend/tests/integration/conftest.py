"""Fixtures for integration tests: local HTTP target server + Patchright browser."""
import asyncio
import json
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler

import pytest


class _TargetHandler(BaseHTTPRequestHandler):
    """Minimal HTTP handler returning predictable JSON responses."""

    def do_GET(self):
        if self.path == "/api/data":
            body = json.dumps({"items": [1, 2, 3], "total": 3}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.write(body)
        elif self.path == "/page":
            html = b"""<!DOCTYPE html>
<html><body>
<h1>Test Page</h1>
<script>
setTimeout(() => fetch('/api/data'), 500);
</script>
</body></html>"""
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.send_header("Content-Length", str(len(html)))
            self.end_headers()
            self.write(html)
        else:
            self.send_response(404)
            self.end_headers()

    def write(self, data):
        self.wfile.write(data)

    def log_message(self, format, *args):
        pass  # suppress logs


@pytest.fixture(scope="session")
def target_server():
    """Start a local HTTP server on a random port, return base URL."""
    server = HTTPServer(("127.0.0.1", 0), _TargetHandler)
    port = server.server_address[1]
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield f"http://127.0.0.1:{port}"
    server.shutdown()


@pytest.fixture(scope="session")
def event_loop():
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()


@pytest.fixture(scope="session")
def browser(event_loop):
    """Launch a real Patchright headless Chromium browser."""
    from patchright.async_api import async_playwright

    pw = event_loop.run_until_complete(async_playwright().start())
    br = event_loop.run_until_complete(pw.chromium.launch(headless=True))
    yield br
    event_loop.run_until_complete(br.close())
    event_loop.run_until_complete(pw.stop())

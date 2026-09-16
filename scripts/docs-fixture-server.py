#!/usr/bin/env python3
"""A loopback endpoint for the documentation forge (v2.163.0).

API Studio's picture is a RESPONSE — status, timing, headers, body, and the
security grade this product computes from those headers. An unsent request
shows none of it, and a send with nothing listening paints a failure. So the
forge serves the one route the product's own starter request asks for,
``/health``, for the length of a run.

Loopback only, GET only, one fixed body: it answers the IDE running beside
it and nothing else can reach it. The headers are the ones HeaderGrader
looks for, so the picture shows a real grade computed from a real response
rather than an invented one.
"""
import http.server
import json
import sys

BODY = json.dumps({"status": "ok", "uptime": 1043, "version": "2.163.0"}, indent=2).encode()

HEADERS = [
    ("Content-Type", "application/json; charset=utf-8"),
    ("Strict-Transport-Security", "max-age=31536000; includeSubDomains"),
    ("Content-Security-Policy", "default-src 'none'"),
    ("X-Content-Type-Options", "nosniff"),
    ("X-Frame-Options", "DENY"),
    ("Referrer-Policy", "no-referrer"),
    ("Permissions-Policy", "geolocation=(), camera=()"),
]


class Health(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):  # noqa: N802 - the base class names it
        self.send_response(200)
        for name, value in HEADERS:
            self.send_header(name, value)
        self.send_header("Content-Length", str(len(BODY)))
        self.end_headers()
        self.wfile.write(BODY)

    def log_message(self, *args):
        pass  # the forge's own output is the only thing worth reading


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
    http.server.HTTPServer(("127.0.0.1", port), Health).serve_forever()

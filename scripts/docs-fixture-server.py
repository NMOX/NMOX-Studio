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

v2.164.0: with a second argument, every other GET path is a file under that
directory — the shop front the DevTools picture picks from, written there by
the forge's own scene during the run. Paths resolve inside the directory or
answer 404; only .html and .css are served.
"""
import http.server
import json
import os
import sys

DOCROOT = None
TYPES = {".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8"}

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
        path = self.path.split("?", 1)[0]
        if path != "/health":
            return self.static(path)
        self.send_response(200)
        for name, value in HEADERS:
            self.send_header(name, value)
        self.send_header("Content-Length", str(len(BODY)))
        self.end_headers()
        self.wfile.write(BODY)

    def static(self, path):
        body, kind = None, None
        if DOCROOT:
            rel = path.lstrip("/") or "index.html"
            target = os.path.realpath(os.path.join(DOCROOT, rel))
            root = os.path.realpath(DOCROOT)
            ext = os.path.splitext(target)[1]
            if target.startswith(root + os.sep) and ext in TYPES and os.path.isfile(target):
                with open(target, "rb") as f:
                    body, kind = f.read(), TYPES[ext]
        if body is None:
            body, kind = b"not found", "text/plain; charset=utf-8"
            self.send_response(404)
        else:
            self.send_response(200)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass  # the forge's own output is the only thing worth reading


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
    DOCROOT = sys.argv[2] if len(sys.argv) > 2 else None
    http.server.HTTPServer(("127.0.0.1", port), Health).serve_forever()

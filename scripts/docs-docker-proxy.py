#!/usr/bin/env python3
"""A read-only, filtered view of the local Docker daemon for the docs forge.

The Docker Panel lists EVERY container, image, volume and network the
daemon holds. On a developer's machine that is their own work, and a
picture of it would publish it into the documentation. So the forge
points the app at this proxy (DOCKER_HOST=tcp://127.0.0.1:<port>) instead:

  * only containers labelled org.nmox.docs=1 exist, and only the images
    those containers run, the volumes carrying the same label, and the
    three built-in networks;
  * every request that is not a GET or HEAD is refused, so nothing the
    app does during a forge run can change the daemon;
  * any endpoint not listed below answers 404.

The daemon is real: the status, ports and CPU of the one demo container
are whatever Docker says they are.

Usage: docs-docker-proxy.py <port> <daemon unix socket path>
Loopback only, standard library only.
"""
import http.client
import http.server
import json
import re
import socket
import sys

LABEL = "org.nmox.docs"
NETWORKS = {"bridge", "host", "none"}
API = re.compile(r"^(/v[0-9.]+)?(/[^?]*)(\?.*)?$")


class UnixConnection(http.client.HTTPConnection):
    def __init__(self, path):
        super().__init__("localhost", timeout=30)
        self.unix_path = path

    def connect(self):
        s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        s.settimeout(30)
        s.connect(self.unix_path)
        self.sock = s


def labelled(labels):
    return (labels or {}).get(LABEL) == "1"


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    daemon_socket = None

    def log_message(self, fmt, *args):  # quiet: the forge log is the app's
        pass

    def upstream(self, method, path):
        conn = UnixConnection(self.daemon_socket)
        try:
            conn.request(method, path)
            resp = conn.getresponse()
            body = resp.read()
            return resp.status, dict(resp.getheaders()), body
        finally:
            conn.close()

    def answer(self, status, body, headers=None):
        self.send_response(status)
        for key in ("Content-Type", "Api-Version", "Docker-Experimental", "Ostype", "Server"):
            if headers and key in headers:
                self.send_header(key, headers[key])
        if not headers or "Content-Type" not in headers:
            self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def refuse(self, status, message):
        self.answer(status, json.dumps({"message": message}).encode())

    def allowed_containers(self, prefix):
        _, _, body = self.upstream("GET", prefix + "/containers/json?all=1")
        return [c for c in json.loads(body) if labelled(c.get("Labels"))]

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        m = API.match(self.path)
        if not m:
            return self.refuse(404, "not in the docs view")
        prefix, route, _ = m.group(1) or "", m.group(2), m.group(3)
        route = route.rstrip("/") or "/"
        if route in ("/_ping", "/version"):
            status, headers, body = self.upstream(self.command, self.path)
            return self.answer(status, body, headers)
        if route == "/containers/json":
            status, headers, body = self.upstream("GET", self.path)
            if status != 200:
                return self.answer(status, body, headers)
            kept = [c for c in json.loads(body) if labelled(c.get("Labels"))]
            return self.answer(200, json.dumps(kept).encode(), headers)
        if route.startswith("/containers/"):
            ref = route.split("/")[2]
            for c in self.allowed_containers(prefix):
                names = [n.lstrip("/") for n in c.get("Names", [])]
                if ref in names or (len(ref) >= 4 and c["Id"].startswith(ref)):
                    status, headers, body = self.upstream("GET", self.path)
                    return self.answer(status, body, headers)
            return self.refuse(404, "No such container: " + ref)
        if route == "/images/json":
            status, headers, body = self.upstream("GET", self.path)
            if status != 200:
                return self.answer(status, body, headers)
            used = {c.get("ImageID") for c in self.allowed_containers(prefix)}
            kept = [i for i in json.loads(body) if i.get("Id") in used]
            return self.answer(200, json.dumps(kept).encode(), headers)
        if route == "/volumes":
            status, headers, body = self.upstream("GET", self.path)
            if status != 200:
                return self.answer(status, body, headers)
            doc = json.loads(body)
            doc["Volumes"] = [v for v in doc.get("Volumes") or [] if labelled(v.get("Labels"))]
            return self.answer(200, json.dumps(doc).encode(), headers)
        if route == "/networks":
            status, headers, body = self.upstream("GET", self.path)
            if status != 200:
                return self.answer(status, body, headers)
            kept = [n for n in json.loads(body) if n.get("Name") in NETWORKS]
            return self.answer(200, json.dumps(kept).encode(), headers)
        if route == "/system/df":
            status, headers, body = self.upstream("GET", self.path)
            if status != 200:
                return self.answer(status, body, headers)
            doc = json.loads(body)
            containers = [c for c in doc.get("Containers") or [] if labelled(c.get("Labels"))]
            used = {c.get("ImageID") for c in containers}
            doc["Containers"] = containers
            doc["Images"] = [i for i in doc.get("Images") or [] if i.get("Id") in used]
            doc["Volumes"] = [v for v in doc.get("Volumes") or [] if labelled(v.get("Labels"))]
            doc["BuildCache"] = []
            doc["LayersSize"] = sum(i.get("Size", 0) for i in doc["Images"])
            # newer APIs (1.52+) also carry per-kind summaries, which the CLI
            # prefers: rebuild them from the filtered lists, or the counts of
            # everything else on the machine would show through
            if "ContainerUsage" in doc:
                doc["ContainerUsage"] = {
                    "ActiveCount": sum(1 for c in containers if c.get("State") == "running"),
                    "Items": containers, "TotalCount": len(containers),
                    "TotalSize": sum(c.get("SizeRw", 0) for c in containers)}
            if "ImageUsage" in doc:
                doc["ImageUsage"] = {
                    "ActiveCount": len(doc["Images"]), "Items": doc["Images"], "Reclaimable": 0,
                    "TotalCount": len(doc["Images"]), "TotalSize": doc["LayersSize"]}
            if "VolumeUsage" in doc:
                doc["VolumeUsage"] = {
                    "ActiveCount": len(doc["Volumes"]), "Items": doc["Volumes"],
                    "TotalCount": len(doc["Volumes"]),
                    "TotalSize": sum((v.get("UsageData") or {}).get("Size", 0) for v in doc["Volumes"])}
            if "BuildCacheUsage" in doc:
                doc["BuildCacheUsage"] = {"Items": [], "TotalCount": 0, "TotalSize": 0}
            return self.answer(200, json.dumps(doc).encode(), headers)
        return self.refuse(404, "not in the docs view")

    def do_POST(self):
        self.refuse(403, "the docs forge's Docker view is read-only")

    do_PUT = do_POST
    do_DELETE = do_POST
    do_PATCH = do_POST


def main():
    if len(sys.argv) != 3:
        print(__doc__.strip().splitlines()[0], file=sys.stderr)
        print("usage: docs-docker-proxy.py <port> <daemon unix socket path>", file=sys.stderr)
        sys.exit(2)
    Handler.daemon_socket = sys.argv[2]
    server = http.server.ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Handler)
    server.serve_forever()


if __name__ == "__main__":
    main()

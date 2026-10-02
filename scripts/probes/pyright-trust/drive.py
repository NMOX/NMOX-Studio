#!/usr/bin/env python3
"""Drive pyright-langserver over stdio: initialize, open one file, listen, report."""
import json, os, subprocess, sys, threading, time, pathlib

server, cwd, root, target, seconds = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], float(sys.argv[5])
root_uri = None if root == "-" else pathlib.Path(root).as_uri()
env = dict(os.environ)
p = subprocess.Popen([server, "--stdio"], cwd=cwd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                     stderr=subprocess.DEVNULL, env=env)

def send(msg):
    body = json.dumps(msg).encode()
    p.stdin.write(b"Content-Length: %d\r\n\r\n" % len(body) + body)
    p.stdin.flush()

diags, logs = [], []
def reader():
    while True:
        header = b""
        while not header.endswith(b"\r\n\r\n"):
            c = p.stdout.read(1)
            if not c:
                return
            header += c
        n = int([l for l in header.decode().split("\r\n") if l.lower().startswith("content-length")][0].split(":")[1])
        msg = json.loads(p.stdout.read(n))
        if "method" in msg and "id" in msg:            # a request from the server
            if msg["method"] == "workspace/configuration":
                forced = os.environ.get("PYR_PYTHONPATH")
                send({"jsonrpc": "2.0", "id": msg["id"], "result": [
                    ({"pythonPath": forced} if forced and it.get("section") == "python" else None)
                    for it in msg["params"]["items"]]})
            else:
                send({"jsonrpc": "2.0", "id": msg["id"], "result": None})
        elif msg.get("method") == "textDocument/publishDiagnostics":
            diags.append((msg["params"]["uri"].rsplit("/", 1)[-1], [d["message"][:70] for d in msg["params"]["diagnostics"]]))
        elif msg.get("method") == "window/logMessage":
            logs.append(msg["params"]["message"][:200])

threading.Thread(target=reader, daemon=True).start()
send({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
    "processId": os.getpid(), "rootUri": root_uri,
    "workspaceFolders": None if root_uri is None else [{"uri": root_uri, "name": "proj"}],
    "capabilities": {"workspace": {"configuration": True, "workspaceFolders": True},
                     "textDocument": {"publishDiagnostics": {}}}}})
time.sleep(1.0)
send({"jsonrpc": "2.0", "method": "initialized", "params": {}})
text = open(target).read()
send({"jsonrpc": "2.0", "method": "textDocument/didOpen", "params": {"textDocument": {
    "uri": pathlib.Path(target).as_uri(), "languageId": "python", "version": 1, "text": text}}})
time.sleep(seconds)
send({"jsonrpc": "2.0", "id": 2, "method": "shutdown"})
time.sleep(0.5)
send({"jsonrpc": "2.0", "method": "exit"})
try:
    p.wait(5)
except Exception:
    p.kill()
print("diagnostics:", diags[-1] if diags else None)
for l in logs:
    if any(w in l for w in ("ython", "config", "venv", "earch path", "nterpreter")):
        print("  log:", l)

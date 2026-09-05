# The Agent Port (MCP)

*Point an AI agent at your IDE — and let it READ, never run.*

NMOX Studio ships a Model Context Protocol server. Any agent that speaks
MCP (Claude Code, an editor assistant, your own script) can connect to
it and ask the IDE what it knows: which project is aimed, what is
serving, what is running, what you are editing, where a name is
declared, what failed last. It is **read-only by construction**: the
build fails if any class in the Agent Port package so much as names a
way to spawn a process, write a file, or stop a run.

## 1. Start it

**Do:** Tools ▸ **Agent Port (MCP)…** ▸ **Start**, then **Copy Config**.

**See:** A dialog with the endpoint (loopback only, a fresh port), a
per-start bearer token, and a ready-made client configuration:

```json
{
  "mcpServers": {
    "nmox-studio": {
      "type": "http",
      "url": "http://127.0.0.1:PORT/mcp",
      "headers": { "Authorization": "Bearer TOKEN" }
    }
  }
}
```

Paste it into your agent's `.mcp.json`. The token exists only in that
dialog — it is never logged or persisted — and dies with the port.
**Stop Agent Port** ends it; so does quitting the IDE.

## 2. The tools

Every tool answers with a human text AND a typed `structuredContent`
under a declared `outputSchema` (the schema is validated against the
real output by the build), and is annotated `readOnlyHint: true`.

| Tool | What it answers | Arguments |
|------|-----------------|-----------|
| `ide_context` | The whole orienting snapshot in one call: project, toolchain, servers, runs, the file being edited, the last failure, a diagnostics count | — |
| `project_state` | The aimed project: name, directory, git branch, detected kind, Node package manager | — |
| `run_history` | The flight recorder's launches and exits, newest first, each exit with its command, code and duration | `limit` |
| `live_servers` | Every dev server the IDE knows is serving, with its URL | — |
| `live_runs` | Every command running right now (what the toolbar ■ would stop), with when it started | — |
| `last_failure` | The most recent failed run: device, command, exit code, up to five error lines | — |
| `diagnostics` | What the linters and checkers currently report | `file` (substring filter) |
| `find_symbol` | Where a name is declared — the same index as Go to Symbol (⌥⇧⌘O) | `query`, `limit` |
| `outline` | One file's structure — the Navigator's own items | `file` |
| `search_text` | Lines containing a literal, case-insensitive, bounded and every cap reported | `query`, `limit` |
| `editor_state` | The active file and every open editor tab, unsaved ones flagged | — |
| `rack_devices` | The devices mounted on the task rack, in order | — |

Every list is bounded and says so: `find_symbol` and `outline` report a
partial index, `search_text` reports `truncated` only when a further
match exists, `run_history` reports when older events were left out.

## 3. Resources and prompts

The same answers are browsable as resources an agent attaches as
context — `nmox://context`, `nmox://project`, `nmox://history`,
`nmox://servers`, `nmox://runs`, `nmox://editor`,
`nmox://last-failure`, `nmox://diagnostics`, `nmox://devices` — plus
two templates for the tools that take an argument:
`nmox://outline/{file}` and `nmox://search/{query}` (percent-encoded).
A resource's text is its tool's structured JSON, byte for byte.

Three prompts fold live state into a question: `diagnose_failure`
(the last failure), `review_setup` (the whole context), and `where_is`
— the one that takes an argument, `name` — which folds the symbol hits
for that name.

## 4. The walk, by hand

With the token in a shell variable (never on a command line you would
paste anywhere):

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_symbol","arguments":{"query":"checkout"}}}'
```

**See:** `checkout (function) — src/cart.js:12`, and the same as
`structuredContent.hits[0]`.

## 5. The refusals are features

| You do | The port says |
|--------|---------------|
| Call without the token, or with a stale one | `401` — nothing else, not even the tool list |
| Call from a page in a browser (any `Origin`) | `403` |
| Anything but `POST` | refused; the port is a request/response server, not a page |
| Read `nmox://nonesuch` | JSON-RPC `-32002` (resource not found) |
| Ask `where_is` without `name` | `-32602`, naming the missing argument |
| Ask for a file outside the project (`../../.zshrc`) | `outline` refuses — *outside the aimed project* — and never reads it |
| Ask it to run, write, or stop anything | there is no such tool; the ledger test keeps it that way |

That last row is the design. An agent that can run your server can also
stop it, and an agent that can write can also delete; the Agent Port
stays a way to ASK. If a future version adds an execution surface it
will arrive with its own consent design, the way ORACLE's outward data
flow did.

See also: the Kitchen Sink's station 24 and the user guide's Agent
Port paragraph.

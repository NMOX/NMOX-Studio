#!/usr/bin/env node
// agent-port-walk.mjs — walk the Agent Port with the OFFICIAL MCP client.
//
// The tutorial's curl walk proves the wire; this proves a real client's
// handshake and every primitive the port declares: tools, resources (a
// template read, subscribe + the event stream, an outline that follows
// its file), prompts, completion, logging. Every step prints OK or FAIL
// with the port's own words; the three "expect a refusal" steps print
// FAIL on purpose — the refusal IS the pass.
//
//   mkdir -p /tmp/nmox-walk && cd /tmp/nmox-walk && npm i @modelcontextprotocol/sdk@1
//   NMOX_MCP_URL=http://127.0.0.1:PORT/mcp NMOX_MCP_TOKEN=... \
//     NODE_PATH=/tmp/nmox-walk/node_modules node scripts/agent-port-walk.mjs [file-to-touch]
//
// Take URL and token from Tools ▸ Agent Port (MCP)… — into shell
// variables, never onto a command line you would paste anywhere. The
// optional file (relative to the aimed project, default shop.js) is
// APPENDED a comment line to prove the outline subscription follows it;
// press ▶ and ■ in the IDE while it listens to see the log messages
// (NMOX_WALK_LISTEN_MS, default 20000, sets how long it listens).
import { createRequire } from "node:module";
const require = createRequire(process.env.NODE_PATH ? process.env.NODE_PATH + "/" : import.meta.url);
const { Client } = require("@modelcontextprotocol/sdk/client/index.js");
const { StreamableHTTPClientTransport } = require("@modelcontextprotocol/sdk/client/streamableHttp.js");
const { ResourceUpdatedNotificationSchema, LoggingMessageNotificationSchema } = require("@modelcontextprotocol/sdk/types.js");
import { appendFileSync, existsSync } from "node:fs";

const url = process.env.NMOX_MCP_URL, token = process.env.NMOX_MCP_TOKEN;
if (!url || !token) { console.log("NO-URL-OR-TOKEN: set NMOX_MCP_URL and NMOX_MCP_TOKEN"); process.exit(2); }
const touchRel = process.argv[2] || "shop.js";
const step = async (name, fn) => { try { const r = await fn(); console.log("OK   " + name + (r ? " → " + r : "")); } catch (e) { console.log("FAIL " + name + " → " + (e && e.message ? e.message.split("\n")[0] : e)); } };
const sleep = ms => new Promise(r => setTimeout(r, ms));

const transport = new StreamableHTTPClientTransport(new URL(url), { requestInit: { headers: { Authorization: "Bearer " + token } } });
const client = new Client({ name: "nmox-agent-port-walk", version: "1.0.0" });
const updated = [], logs = [];
client.setNotificationHandler(ResourceUpdatedNotificationSchema, n => { updated.push(n.params.uri); console.log("     NOTIFIED " + n.params.uri); });
client.setNotificationHandler(LoggingMessageNotificationSchema, n => { logs.push(n.params); console.log("     LOG " + n.params.level + " [" + n.params.logger + "] " + String(n.params.data).slice(0, 80)); });

await step("initialize", async () => { await client.connect(transport); const v = client.getServerVersion(); return v.name + " " + v.version + "; caps=" + Object.keys(client.getServerCapabilities()).sort().join(","); });
let projectDir = null;
await step("tools/list", async () => { const t = await client.listTools(); return t.tools.length + " tools: " + t.tools.map(x => x.name).join(" "); });
await step("tools/call ide_context", async () => { const r = await client.callTool({ name: "ide_context", arguments: {} }); return r.content[0].text.split("\n")[0]; });
await step("tools/call project_state", async () => { const r = await client.callTool({ name: "project_state", arguments: {} }); const s = r.structuredContent || {}; projectDir = s.directory || null; return (s.project || "?") + " (" + (s.kind || "?") + ") at " + projectDir; });
await step("tools/call find_symbol check", async () => { const r = await client.callTool({ name: "find_symbol", arguments: { query: "check" } }); return r.content[0].text.split("\n")[0]; });
await step("tools/call search_text for a value in .env (expect no hits from .env)", async () => { const r = await client.callTool({ name: "search_text", arguments: { query: "=" } }); const files = (r.structuredContent?.matches || []).map(h => h.file); return files.length + " matches in " + (r.structuredContent?.filesScanned ?? "?") + " files; env files among them: " + files.filter(f => /(^|\/)\.env/.test(f)).length; });
await step("resources/list", async () => { const r = await client.listResources(); return r.resources.length + " resources"; });
await step("resources/templates/list", async () => { const r = await client.listResourceTemplates(); return r.resourceTemplates.map(x => x.uriTemplate).join(" "); });
await step("resources/read nmox://outline/" + touchRel, async () => { const r = await client.readResource({ uri: "nmox://outline/" + encodeURIComponent(touchRel) }); return r.contents[0].text.slice(0, 80); });
await step("prompts/list", async () => { const r = await client.listPrompts(); return r.prompts.map(p => p.name).join(" "); });
await step("prompts/get where_is WITHOUT its argument (expect a refusal)", async () => { await client.getPrompt({ name: "where_is", arguments: {} }); return "no refusal!"; });
await step("completion/complete where_is.name 'che'", async () => { const r = await client.complete({ ref: { type: "ref/prompt", name: "where_is" }, argument: { name: "name", value: "che" } }); return JSON.stringify(r.completion); });
await step("completion/complete outline {file} 's'", async () => { const r = await client.complete({ ref: { type: "ref/resource", uri: "nmox://outline/{file}" }, argument: { name: "file", value: "s" } }); return JSON.stringify(r.completion).slice(0, 160); });
await step("resources/subscribe nmox://runs", async () => { await client.subscribeResource({ uri: "nmox://runs" }); return "accepted"; });
await step("resources/subscribe nmox://outline/" + touchRel, async () => { await client.subscribeResource({ uri: "nmox://outline/" + encodeURIComponent(touchRel) }); return "accepted — following the file"; });
await step("resources/subscribe nmox://outline/..%2F..%2F.zshrc (expect a refusal)", async () => { await client.subscribeResource({ uri: "nmox://outline/..%2F..%2F.zshrc" }); return "ACCEPTED — WRONG"; });
await step("logging/setLevel debug", async () => { await client.setLoggingLevel("debug"); return "set"; });
if (projectDir && existsSync(projectDir + "/" + touchRel)) {
  await sleep(2500);
  await step("touch " + touchRel + " and wait for its outline URI", async () => {
    appendFileSync(projectDir + "/" + touchRel, "\n// touched by agent-port-walk " + Date.now() + "\n");
    const want = "nmox://outline/" + encodeURIComponent(touchRel), deadline = Date.now() + 8000;
    while (!updated.includes(want) && Date.now() < deadline) await sleep(100);
    if (!updated.includes(want)) throw new Error("no notification within 8 s");
    return "FOLLOW-PROVEN";
  });
} else {
  console.log("     (project_state names no directory holding " + touchRel + " — the file-follow leg skipped)");
}
const listenMs = Number(process.env.NMOX_WALK_LISTEN_MS || 20000);
console.log("     listening " + Math.round(listenMs / 1000) + " s for pushes and log messages — press ▶ then ■ in the IDE (NMOX_WALK_LISTEN_MS to change)");
await sleep(listenMs);
console.log("     pushes: " + updated.join(" ") + " | log messages: " + logs.length + " (levels " + [...new Set(logs.map(l => l.level))].join(",") + ")");
await step("close", async () => { await client.close(); return "closed"; });

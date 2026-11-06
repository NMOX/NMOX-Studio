// A minimal language-server client for the probe beside it (3.10.0).
//
//   node drive.mjs <workspace> <file> <seconds> <command> [args...]
//
// It declares what the platform's client declares (publishDiagnostics, and
// nothing about asking), opens <file>, waits, and prints three facts:
//   PUSHED n   how many problems the server sent for the file by itself
//   PULLED n   how many it returned when asked (textDocument/diagnostic)
//   SHUTDOWN   what it answered to a shutdown written with "params":null,
//              which is how lsp4j writes one
// Every request the server makes is answered with an error, as lsp4j answers
// a request its client does not implement.
import { spawn } from "node:child_process";
import fs from "node:fs";
import { pathToFileURL } from "node:url";

const [workspace, file, seconds, command, ...args] = process.argv.slice(2);
const child = spawn(command, args, { cwd: workspace, stdio: ["pipe", "pipe", "ignore"] });
let buffer = Buffer.alloc(0);
let next = 0;
const waiting = new Map();
let pushed = null;
const uri = pathToFileURL(file).href;

child.stdout.on("data", chunk => {
    buffer = Buffer.concat([buffer, chunk]);
    for (;;) {
        const head = buffer.indexOf("\r\n\r\n");
        if (head < 0) return;
        const length = Number(/Content-Length: (\d+)/i.exec(buffer.subarray(0, head).toString())[1]);
        if (buffer.length < head + 4 + length) return;
        const message = JSON.parse(buffer.subarray(head + 4, head + 4 + length).toString());
        buffer = buffer.subarray(head + 4 + length);
        if (message.method && message.id !== undefined) {
            send({ jsonrpc: "2.0", id: message.id, error: { code: -32603, message: "Internal error." } });
        } else if (message.id !== undefined) {
            waiting.get(message.id)?.(message);
            waiting.delete(message.id);
        } else if (message.method === "textDocument/publishDiagnostics"
                && message.params.uri.toLowerCase() === uri.toLowerCase()) {
            pushed = message.params.diagnostics.length;
        }
    }
});

function raw(text) {
    child.stdin.write(`Content-Length: ${Buffer.byteLength(text)}\r\n\r\n${text}`);
}
function send(message) {
    raw(JSON.stringify(message));
}
function request(method, params, bodyOverride) {
    return new Promise(resolve => {
        const id = String(++next);
        waiting.set(id, resolve);
        raw(bodyOverride ? bodyOverride(id) : JSON.stringify({ jsonrpc: "2.0", id, method, params }));
        setTimeout(() => resolve({ timeout: true }), 6000);
    });
}
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

const root = pathToFileURL(workspace).href;
await request("initialize", {
    processId: process.pid, rootUri: root, workspaceFolders: [{ uri: root, name: "probe" }],
    capabilities: { textDocument: { synchronization: { didSave: true }, publishDiagnostics: {} }, workspace: { workspaceFolders: true } },
});
send({ jsonrpc: "2.0", method: "initialized", params: {} });
send({ jsonrpc: "2.0", method: "textDocument/didOpen",
    params: { textDocument: { uri, languageId: "typescript", version: 1, text: fs.readFileSync(file, "utf8") } } });
await wait(Number(seconds) * 1000);
console.log("PUSHED", pushed === null ? "nothing" : pushed);
const pulled = await request("textDocument/diagnostic", { textDocument: { uri } });
console.log("PULLED", pulled.result?.items ? pulled.result.items.length : JSON.stringify(pulled.error ?? pulled).slice(0, 120));
const down = await request("shutdown", null, id => `{"jsonrpc":"2.0","id":"${id}","method":"shutdown","params":null}`);
console.log("SHUTDOWN", down.error ? "refused: " + down.error.message : down.timeout ? "no answer" : "ok");
child.kill();
process.exit(0);

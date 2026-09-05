package org.nmox.studio.rack.mcp;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.rack.mcp.McpTools.Tool;

/**
 * The pure JSON-RPC half of the Agent Port (futures-2031 F4): one
 * static method turns a request string into a response string against
 * a tool registry, so every protocol rule is a plain unit test. Speaks
 * the MCP Streamable-HTTP dialect's message layer — initialize,
 * tools/list, tools/call, ping — and refuses everything else by code:
 * -32700 unparseable, -32600 not-a-request, -32601 unknown method,
 * -32602 bad params. A notification (no id) answers null and the
 * transport sends 202-and-nothing, per spec.
 */
public final class McpProtocol {

    private McpProtocol() {
    }

    /** The spec revision this server implements. */
    public static final String PROTOCOL_VERSION = "2025-06-18";

    /** Server identity, shown in the client's server list. */
    public static final String SERVER_NAME = "nmox-studio";

    /**
     * Handles one JSON-RPC message. Returns the response JSON, or null
     * for a notification (nothing to say back).
     */
    public static String handle(String requestJson, McpTools tools,
            String productVersion) {
        return handle(requestJson, tools, productVersion, null);
    }

    /** As above, with the port's subscriptions (v2.84.0); null = no subscriptions are kept. */
    public static String handle(String requestJson, McpTools tools,
            String productVersion, McpSubscriptions subs) {
        return handleWith(requestJson, tools, productVersion, subs, McpCompletions.production());
    }

    /** As above with an explicit completer (v2.84.0) — the port's, or a test's. */
    static String handle(String requestJson, McpTools tools,
            String productVersion, McpSubscriptions subs, McpCompletions completions) {
        return handleWith(requestJson, tools, productVersion, subs, completions);
    }

    private static String handleWith(String requestJson, McpTools tools,
            String productVersion, McpSubscriptions subs, McpCompletions completions) {
        JSONObject request;
        try {
            request = new JSONObject(requestJson == null ? "" : requestJson);
        } catch (RuntimeException notJson) {
            return error(JSONObject.NULL, -32700, "Parse error").toString();
        }
        Object id = request.opt("id");
        String method = request.optString("method", "");
        if (method.isBlank()) {
            return error(id == null ? JSONObject.NULL : id, -32600,
                    "Invalid request — no method").toString();
        }
        boolean notification = id == null;
        JSONObject params = request.optJSONObject("params");
        JSONObject result;
        switch (method) {
            case "initialize" ->
                result = initialize(productVersion);
            case "notifications/initialized", "notifications/cancelled" -> {
                return null; // acknowledged by silence, per spec
            }
            case "ping" ->
                result = new JSONObject();
            case "tools/list" ->
                result = toolsList(tools);
            case "tools/call" -> {
                if (notification) {
                    return null; // a call with no id has no reply channel
                }
                return toolsCall(id, params, tools).toString();
            }
            case "resources/list" ->
                result = McpResources.list(tools);
            case "resources/templates/list" ->
                result = McpResources.templates(tools);
            case "resources/subscribe", "resources/unsubscribe" -> {
                if (notification) {
                    return null;
                }
                String uri = params == null ? null : params.optString("uri", null);
                if (uri == null) {
                    return error(id, -32602, method + " needs params.uri").toString();
                }
                if (!McpResources.isCatalogued(uri, tools)) {
                    return error(id, -32002, "Resource not found: " + uri).toString();
                }
                if (subs != null) {
                    if (method.equals("resources/subscribe")) {
                        subs.subscribe(uri);
                    } else {
                        subs.unsubscribe(uri);
                    }
                }
                result = new JSONObject();
            }
            case "resources/read" -> {
                if (notification) {
                    return null;
                }
                String uri = params == null ? null : params.optString("uri", null);
                if (uri == null) {
                    return error(id, -32602, "resources/read needs params.uri").toString();
                }
                JSONObject read;
                try {
                    read = McpResources.read(uri, tools);
                } catch (RuntimeException ex) {
                    // a resource is a view over a tool handler — a live-state
                    // throw here must answer as JSON-RPC, exactly as
                    // tools/call does, never escape to the transport (the
                    // v2.56.1 review find: only tools/call was guarded)
                    return error(id, -32603, "Internal error: " + ex.getMessage()).toString();
                }
                // the spec's dedicated code for an unknown resource URI
                return read == null
                        ? error(id, -32002, "Resource not found: " + uri).toString()
                        : response(id, read).toString();
            }
            case "completion/complete" -> {
                if (notification) {
                    return null;
                }
                try {
                    result = completions.complete(params);
                } catch (IllegalArgumentException bad) {
                    return error(id, -32602, bad.getMessage()).toString();
                } catch (RuntimeException ex) {
                    return error(id, -32603, "Internal error: " + ex.getMessage()).toString();
                }
            }
            case "prompts/list" ->
                result = McpPrompts.list();
            case "prompts/get" -> {
                if (notification) {
                    return null;
                }
                String name = params == null ? null : params.optString("name", null);
                if (name == null) {
                    return error(id, -32602, "prompts/get needs params.name").toString();
                }
                JSONObject prompt;
                try {
                    prompt = McpPrompts.get(name, tools, params.optJSONObject("arguments"));
                } catch (IllegalArgumentException missing) {
                    return error(id, -32602, missing.getMessage()).toString();
                } catch (RuntimeException ex) {
                    return error(id, -32603, "Internal error: " + ex.getMessage()).toString();
                }
                return prompt == null
                        ? error(id, -32602, "Unknown prompt: " + name).toString()
                        : response(id, prompt).toString();
            }
            default -> {
                if (notification) {
                    return null; // unknown notifications are ignored, per spec
                }
                return error(id, -32601, "Method not found: " + method).toString();
            }
        }
        return notification ? null : response(id, result).toString();
    }

    private static JSONObject initialize(String productVersion) {
        return new JSONObject()
                .put("protocolVersion", PROTOCOL_VERSION)
                // all three read-only primitives declared: an agent
                // framework knows to browse resources and offer prompts,
                // not just call tools (v2.56.0)
                .put("capabilities", new JSONObject()
                        .put("tools", new JSONObject())
                        // subscribe: an agent learns a run started or a server
                        // went live over the GET stream, no polling (v2.84.0)
                        .put("resources", new JSONObject().put("subscribe", true))
                        .put("prompts", new JSONObject())
                        .put("completions", new JSONObject()))
                .put("serverInfo", new JSONObject()
                        .put("name", SERVER_NAME)
                        .put("version", productVersion))
                // an agent reads this before any tool: name what the port
                // answers, incl. the v2.77–v2.81 tools (v2.84.0 currency)
                .put("instructions", "NMOX Studio's read-only state. Start with "
                        + "ide_context. Then: project_state (toolchain, package "
                        + "manager, branch), live_servers, live_runs (what the "
                        + "toolbar stop would end), run_history, last_failure, "
                        + "diagnostics, find_symbol (where a name is declared), "
                        + "outline (one file's structure), search_text (a literal "
                        + "across the project), editor_state (what is open), "
                        + "rack_devices. The same answers are nmox:// resources. "
                        + "Nothing here executes, edits, or stops anything.");
    }

    private static JSONObject toolsList(McpTools tools) {
        JSONArray defs = new JSONArray();
        for (Tool t : tools.all()) {
            JSONObject def = new JSONObject()
                    .put("name", t.name())
                    .put("title", t.title())
                    .put("description", t.description())
                    .put("inputSchema", t.inputSchema())
                    .put("outputSchema", t.outputSchema())
                    // annotations advertise the read-only guarantee IN the
                    // protocol — an agent framework can trust the safety of
                    // every tool without reading our docs (the arc's law,
                    // stated where the spec puts it)
                    .put("annotations", new JSONObject()
                            .put("title", t.title())
                            .put("readOnlyHint", true)
                            .put("destructiveHint", false)
                            .put("idempotentHint", true)
                            .put("openWorldHint", false));
            defs.put(def);
        }
        return new JSONObject().put("tools", defs);
    }

    private static JSONObject toolsCall(Object id, JSONObject params,
            McpTools tools) {
        String name = params == null ? null : params.optString("name", null);
        if (name == null) {
            return error(id, -32602, "tools/call needs params.name");
        }
        Tool tool = tools.byName(name);
        if (tool == null) {
            return error(id, -32602, "Unknown tool: " + name);
        }
        JSONObject arguments = params.optJSONObject("arguments");
        if (arguments == null) {
            arguments = new JSONObject();
        }
        McpTools.ToolResult answer;
        boolean isError = false;
        try {
            answer = tool.handler().apply(arguments);
        } catch (RuntimeException ex) {
            // a tool failure is a RESULT with isError, not a protocol
            // error — the spec's channel for it, and the message stays
            // honest without a stack trace
            answer = new McpTools.ToolResult(
                    "Tool failed: " + ex.getMessage(), null);
            isError = true;
        }
        JSONObject result = new JSONObject()
                // the text block is kept for human/legacy clients; the
                // structuredContent is what a programmatic agent reads,
                // validated by the tool's declared outputSchema
                .put("content", new JSONArray().put(new JSONObject()
                        .put("type", "text").put("text", answer.text())))
                .put("isError", isError);
        if (answer.structured() != null) {
            result.put("structuredContent", answer.structured());
        }
        return response(id, result);
    }

    private static JSONObject response(Object id, JSONObject result) {
        return new JSONObject().put("jsonrpc", "2.0").put("id", id)
                .put("result", result);
    }

    private static JSONObject error(Object id, int code, String message) {
        return new JSONObject().put("jsonrpc", "2.0").put("id", id)
                .put("error", new JSONObject()
                        .put("code", code).put("message", message));
    }

    /** The tool roster as prose for the start dialog's disclosure. */
    public static String disclosure(McpTools tools) {
        StringBuilder sb = new StringBuilder();
        List<Tool> all = tools.all();
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(all.get(i).name());
        }
        return sb.toString();
    }
}

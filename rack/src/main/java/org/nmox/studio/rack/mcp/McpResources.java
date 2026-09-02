package org.nmox.studio.rack.mcp;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The Agent Port's Resources primitive — the canonical read-only face
 * of MCP, and idiomatic where Tools are the imperative one: an agent
 * framework LISTS and READS resources the way it browses a filesystem,
 * attaching them as context. Every resource here is a URI-addressed
 * VIEW over a {@link McpTools} tool's structured output — the same
 * single source of truth (v2.55.0), so reading {@code nmox://context}
 * returns byte-identical JSON to calling {@code ide_context}, and the
 * read-only guarantee is inherited, not re-argued: a resource can only
 * invoke a tool handler, and every tool handler is ledger-pinned pure.
 */
final class McpResources {

    private McpResources() {
    }

    /** A resource's stable URI paired with the tool that answers it. */
    private record Bound(String uri, String toolName) {
    }

    // the catalog: a resource per read-only tool. ide_context leads
    // (the orienting one); the URIs are stable contract, the tool names
    // the implementation behind them.
    private static final List<Bound> CATALOG = List.of(
            new Bound("nmox://context", "ide_context"),
            new Bound("nmox://project", "project_state"),
            new Bound("nmox://servers", "live_servers"),
            new Bound("nmox://last-failure", "last_failure"),
            new Bound("nmox://diagnostics", "diagnostics"),
            new Bound("nmox://devices", "rack_devices"));

    static final String MIME = "application/json";

    /** The resources/list payload: one entry per bound tool. */
    static JSONObject list(McpTools tools) {
        JSONArray resources = new JSONArray();
        for (Bound b : CATALOG) {
            McpTools.Tool t = tools.byName(b.toolName());
            if (t == null) {
                continue; // a catalog entry with no tool is simply absent
            }
            resources.put(new JSONObject()
                    .put("uri", b.uri())
                    .put("name", b.toolName())
                    .put("title", t.title())
                    .put("description", t.description())
                    .put("mimeType", MIME));
        }
        return new JSONObject().put("resources", resources);
    }

    /**
     * The resources/read payload for {@code uri}, or null when the URI
     * is not in the catalog (the caller returns the spec's
     * resource-not-found error). The text is the bound tool's structured
     * JSON verbatim — resources and tools can never disagree.
     */
    static JSONObject read(String uri, McpTools tools) {
        for (Bound b : CATALOG) {
            if (b.uri().equals(uri)) {
                McpTools.Tool t = tools.byName(b.toolName());
                if (t == null) {
                    return null;
                }
                // empty arguments: a resource read is the no-parameter
                // form of its tool (diagnostics reads unfiltered)
                JSONObject structured = t.handler().apply(new JSONObject()).structured();
                JSONArray contents = new JSONArray().put(new JSONObject()
                        .put("uri", uri)
                        .put("mimeType", MIME)
                        .put("text", structured == null ? "{}" : structured.toString()));
                return new JSONObject().put("contents", contents);
            }
        }
        return null;
    }
}

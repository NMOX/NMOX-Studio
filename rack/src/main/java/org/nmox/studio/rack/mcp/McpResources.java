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
            new Bound("nmox://runs", "live_runs"),
            new Bound("nmox://editor", "editor_state"),
            new Bound("nmox://last-failure", "last_failure"),
            new Bound("nmox://history", "run_history"),
            new Bound("nmox://diagnostics", "diagnostics"),
            new Bound("nmox://devices", "rack_devices"));

    /** A parameterised resource (v2.80.0): the tool behind it and the
     *  argument the URI's tail fills, percent-decoded. */
    private record Template(String uriTemplate, String toolName, String argument,
            String title, String description) {
        String prefix() {
            return uriTemplate.substring(0, uriTemplate.indexOf('{'));
        }
    }

    // resource templates (the spec's resources/templates/list): the two
    // tools that take an argument, browsable as URIs — an agent that
    // attaches nmox://outline/src/app.js gets the outline as context
    private static final List<Template> TEMPLATES = List.of(
            new Template("nmox://outline/{file}", "outline", "file",
                    "Outline of a file", "The Navigator's items for one file of the aimed project (path relative to it, percent-encoded)."),
            new Template("nmox://search/{query}", "search_text", "query",
                    "Text search", "Lines in the aimed project containing a literal (percent-encoded), bounded."));

    static final String MIME = "application/json";

    /** The resources/templates/list payload: one entry per template whose tool exists. */
    static JSONObject templates(McpTools tools) {
        JSONArray out = new JSONArray();
        for (Template t : TEMPLATES) {
            if (tools.byName(t.toolName()) == null) {
                continue;
            }
            out.put(new JSONObject()
                    .put("uriTemplate", t.uriTemplate())
                    .put("name", t.toolName())
                    .put("title", t.title())
                    .put("description", t.description())
                    .put("mimeType", MIME));
        }
        return new JSONObject().put("resourceTemplates", out);
    }

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
        for (Template t : TEMPLATES) {
            if (uri.startsWith(t.prefix()) && uri.length() > t.prefix().length()) {
                McpTools.Tool tool = tools.byName(t.toolName());
                if (tool == null) {
                    return null;
                }
                String value;
                try {
                    // URLDecoder speaks FORM encoding, where '+' is a space; a
                    // URI path's '+' is a literal (v2.83.0 review find: a file
                    // named a+b.js could never be addressed) — keep it literal
                    value = java.net.URLDecoder.decode(
                            uri.substring(t.prefix().length()).replace("+", "%2B"),
                            java.nio.charset.StandardCharsets.UTF_8);
                } catch (IllegalArgumentException malformed) {
                    return null; // a broken percent-escape names nothing
                }
                JSONObject structured = tool.handler().apply(new JSONObject().put(t.argument(), value)).structured();
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

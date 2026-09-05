package org.nmox.studio.rack.mcp;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The Agent Port's Prompts primitive — server-offered templates that
 * fold the IDE's LIVE state into a ready question, so a developer's
 * agent can run "diagnose my last failure" without the developer
 * pasting anything. Each prompt reads a {@link McpTools} tool's text
 * face (the single source of truth again) and wraps it in a user
 * message; reading state to BUILD a prompt is still pure reading, so
 * the read-only law holds. Templates take no arguments — the live
 * state IS the argument.
 */
final class McpPrompts {

    private McpPrompts() {
    }

    private record Template(String name, String title, String description,
            String toolName, String frame) {
    }

    // the frame carries a {state} placeholder the live tool text fills.
    private static final List<Template> CATALOG = List.of(
            new Template("diagnose_failure", "Diagnose the last failure",
                    "Asks for a diagnosis of the most recent failed run, "
                    + "with the IDE's captured failure context folded in.",
                    "last_failure",
                    "Here is the most recent failed run in my IDE:\n\n{state}\n\n"
                    + "What most likely went wrong, and what is the concrete "
                    + "next step to fix it?"),
            new Template("review_setup", "Review what's running",
                    "Asks for a sanity check of the current dev setup — the "
                    + "aimed project, everything serving, everything running.",
                    "ide_context",
                    "This is my current development setup in NMOX Studio:\n\n"
                    + "{state}\n\nDoes anything look off or worth checking?"));

    /** The prompts/list payload. */
    static JSONObject list() {
        JSONArray prompts = new JSONArray();
        for (Template t : CATALOG) {
            prompts.put(new JSONObject()
                    .put("name", t.name())
                    .put("title", t.title())
                    .put("description", t.description())
                    .put("arguments", new JSONArray()));
        }
        return new JSONObject().put("prompts", prompts);
    }

    /**
     * The prompts/get payload for {@code name}, or null when unknown
     * (the caller returns an invalid-params error). Folds the bound
     * tool's live text into the frame.
     */
    static JSONObject get(String name, McpTools tools) {
        for (Template tpl : CATALOG) {
            if (tpl.name().equals(name)) {
                McpTools.Tool tool = tools.byName(tpl.toolName());
                String state = tool == null ? "(unavailable)"
                        : tool.handler().apply(new JSONObject()).text();
                String text = tpl.frame().replace("{state}", state);
                JSONArray messages = new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("content", new JSONObject()
                                .put("type", "text").put("text", text)));
                return new JSONObject()
                        .put("description", tpl.description())
                        .put("messages", messages);
            }
        }
        return null;
    }
}

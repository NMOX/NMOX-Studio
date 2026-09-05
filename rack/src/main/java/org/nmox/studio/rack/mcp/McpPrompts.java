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
            String toolName, String frame, String argument, String argumentDescription) {
        Template(String name, String title, String description, String toolName, String frame) {
            this(name, title, description, toolName, frame, null, null);
        }
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
                    + "{state}\n\nDoes anything look off or worth checking?"),
            // the first prompt WITH an argument (v2.80.0): the name is the
            // agent's, the answer is the IDE's own symbol index
            new Template("where_is", "Where is a symbol declared",
                    "Asks about a name in the aimed project, with the IDE's own "
                    + "symbol hits folded in.",
                    "find_symbol",
                    "Here is where \"{argument}\" is declared in my project, "
                    + "from my IDE's symbol index:\n\n{state}\n\nWhat does it do, "
                    + "and what should I read next to understand it?",
                    "name", "The symbol to look for (a function, class, route, selector...)."));

    /** The prompts/list payload. */
    static JSONObject list() {
        JSONArray prompts = new JSONArray();
        for (Template t : CATALOG) {
            JSONArray arguments = new JSONArray();
            if (t.argument() != null) {
                arguments.put(new JSONObject()
                        .put("name", t.argument())
                        .put("description", t.argumentDescription())
                        .put("required", true));
            }
            prompts.put(new JSONObject()
                    .put("name", t.name())
                    .put("title", t.title())
                    .put("description", t.description())
                    .put("arguments", arguments));
        }
        return new JSONObject().put("prompts", prompts);
    }

    /**
     * The prompts/get payload for {@code name}, or null when unknown
     * (the caller returns an invalid-params error). Folds the bound
     * tool's live text into the frame.
     */
    static JSONObject get(String name, McpTools tools) {
        return get(name, tools, null);
    }

    /**
     * As above with the request's {@code arguments}; a template that
     * declares an argument refuses without it — {@link IllegalArgumentException}
     * names the missing one, which the protocol answers as -32602.
     */
    /** Whether prompt {@code name} declares {@code argument} (v2.84.0, for completion/complete). */
    static boolean hasArgument(String name, String argument) {
        for (Template tpl : CATALOG) {
            if (tpl.name().equals(name)) {
                return tpl.argument() != null && tpl.argument().equals(argument);
            }
        }
        return false;
    }

    static JSONObject get(String name, McpTools tools, JSONObject arguments) {
        for (Template tpl : CATALOG) {
            if (tpl.name().equals(name)) {
                McpTools.Tool tool = tools.byName(tpl.toolName());
                JSONObject args = new JSONObject();
                String value = "";
                if (tpl.argument() != null) {
                    value = arguments == null ? "" : arguments.optString(tpl.argument(), "").strip();
                    if (value.isEmpty()) {
                        throw new IllegalArgumentException(
                                "prompt " + name + " needs arguments." + tpl.argument());
                    }
                    // the tool's own argument name differs from the prompt's
                    // (find_symbol takes "query"): the prompt is the agent's
                    // vocabulary, the tool keeps its schema
                    args.put("query", value);
                }
                String state = tool == null ? "(unavailable)"
                        : tool.handler().apply(args).text();
                String text = tpl.frame().replace("{state}", state).replace("{argument}", value);
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

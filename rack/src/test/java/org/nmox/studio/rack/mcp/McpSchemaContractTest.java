package org.nmox.studio.rack.mcp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.nmox.studio.rack.engine.OracleClient.FailureContext;
import org.nmox.studio.rack.service.ServingRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The schema contract (v2.56.1 review): every production tool's REAL
 * structured output must validate against the outputSchema that tool
 * declares. The v2.55.0 walk checked that a schema was PRESENT, never
 * that output validated against it — and four of six declared
 * additionalProperties:false while emitting undeclared keys. A tiny
 * structural validator (types incl. null-unions, properties,
 * additionalProperties, required, items, enum) is enough to make the
 * declaration a law: any drift between a builder and its schema fails
 * the build by name.
 */
class McpSchemaContractTest {

    // ---- the validator -----------------------------------------------------

    static List<String> validate(JSONObject schema, Object value, String path) {
        List<String> v = new ArrayList<>();
        Object typeSpec = schema.opt("type");
        if (typeSpec != null && !typeMatches(typeSpec, value)) {
            v.add(path + ": expected " + typeSpec + " got " + kind(value));
            return v;
        }
        if (schema.has("enum") && !(value == null || JSONObject.NULL.equals(value))) {
            JSONArray allowed = schema.getJSONArray("enum");
            boolean ok = false;
            for (int i = 0; i < allowed.length(); i++) {
                if (allowed.get(i).equals(value)) {
                    ok = true;
                }
            }
            if (!ok) {
                v.add(path + ": " + value + " not in enum " + allowed);
            }
        }
        if (value instanceof JSONObject obj) {
            JSONObject props = schema.optJSONObject("properties");
            boolean closed = !schema.optBoolean("additionalProperties", true);
            for (String key : obj.keySet()) {
                JSONObject sub = props == null ? null : props.optJSONObject(key);
                if (sub == null) {
                    if (closed) {
                        v.add(path + "." + key + ": undeclared key (additionalProperties:false)");
                    }
                    continue;
                }
                v.addAll(validate(sub, obj.get(key), path + "." + key));
            }
            JSONArray required = schema.optJSONArray("required");
            if (required != null) {
                for (int i = 0; i < required.length(); i++) {
                    if (!obj.has(required.getString(i))) {
                        v.add(path + ": missing required " + required.getString(i));
                    }
                }
            }
        } else if (value instanceof JSONArray arr) {
            JSONObject items = schema.optJSONObject("items");
            if (items != null) {
                for (int i = 0; i < arr.length(); i++) {
                    v.addAll(validate(items, arr.get(i), path + "[" + i + "]"));
                }
            }
        }
        return v;
    }

    private static boolean typeMatches(Object typeSpec, Object value) {
        if (typeSpec instanceof JSONArray union) {
            for (int i = 0; i < union.length(); i++) {
                if (typeMatches(union.getString(i), value)) {
                    return true;
                }
            }
            return false;
        }
        String t = typeSpec.toString();
        return switch (t) {
            case "null" -> value == null || JSONObject.NULL.equals(value);
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof JSONArray;
            case "object" -> value instanceof JSONObject;
            default -> false;
        };
    }

    private static String kind(Object value) {
        return value == null || JSONObject.NULL.equals(value)
                ? "null" : value.getClass().getSimpleName();
    }

    // ---- fixtures mirroring live shapes ------------------------------------

    private static final List<ServingRegistry.Serving> SERVINGS = List.of(
            new ServingRegistry.Serving("velocity", "VELOCITY",
                    "http://localhost:5173", ServingRegistry.Kind.WEB, new File("/p")));

    private static final Optional<FailureContext> FAILURE = Optional.of(
            new FailureContext("VERITAS", "npm test", 1,
                    List.of("FAIL src/cart.test.js", "Expected 2, got 3"), "demo", 1200));

    private static final Map<String, List<DiagnosticsBus.Problem>> DIAGS = Map.of(
            "eslint", List.of(
                    new DiagnosticsBus.Problem(new File("/p/src/cart.js"), 12, "unused var", false),
                    new DiagnosticsBus.Problem(new File("/p/src/api.js"), 3, "no-undef", true)));

    private static JSONObject schemaOf(String tool) {
        return McpTools.production().byName(tool).outputSchema();
    }

    private static void assertValid(String tool, JSONObject structured) {
        assertThat(validate(schemaOf(tool), structured, tool))
                .as(tool + " output vs its declared outputSchema")
                .isEmpty();
    }

    // ---- one contract assertion per tool, both branches where they exist ---

    @Test
    @DisplayName("project_state validates aimed and unaimed")
    void projectState() {
        assertValid("project_state", McpTools.projectState(() -> new File("/tmp")));
        assertValid("project_state", McpTools.projectState(() -> null));
    }

    @Test
    @DisplayName("live_servers validates populated and empty")
    void liveServers() {
        assertValid("live_servers", McpTools.liveServers(SERVINGS));
        assertValid("live_servers", McpTools.liveServers(List.of()));
    }

    @Test
    @DisplayName("live_runs validates populated and empty (v2.77.0)")
    void liveRuns() {
        org.nmox.studio.core.spi.LiveRuns.add(new org.nmox.studio.core.spi.LiveRuns.Run(
                "ide-run:/tmp/schema#1", "Run \u2014 schema", () -> { }));
        try {
            assertValid("live_runs", McpTools.liveRuns(org.nmox.studio.core.spi.LiveRuns.live()));
        } finally {
            org.nmox.studio.core.spi.LiveRuns.stopAll();
        }
        assertValid("live_runs", McpTools.liveRuns(List.of()));
    }

    @Test
    @DisplayName("find_symbol validates with hits, empty, and unavailable (v2.78.0)")
    void findSymbol() {
        org.nmox.studio.core.spi.SymbolIndex fake = (root, q, limit) -> new org.nmox.studio.core.spi.SymbolIndex.Answer(
                List.of(new org.nmox.studio.core.spi.SymbolIndex.Hit("a", "FUNCTION", "a.js", 1)), false);
        assertValid("find_symbol", McpTools.findSymbol(fake, new File("/tmp"), "a", 5));
        assertValid("find_symbol", McpTools.findSymbol(fake, new File("/tmp"), "", 5));
        assertValid("find_symbol", McpTools.findSymbol(null, null, "a", 5));
    }

    @Test
    @DisplayName("editor_state validates open, empty, and unavailable (v2.78.0)")
    void editorState() {
        assertValid("editor_state", EditorState.editorState("/p/a.js",
                List.of(new EditorState.OpenFile("/p/a.js", true, true)), null));
        assertValid("editor_state", EditorState.editorState(null, List.of(), null));
        assertValid("editor_state", EditorState.editorState(null, List.of(), "unavailable"));
    }

    @Test
    @DisplayName("last_failure validates failed and clean — exitCode/errorLines declared")
    void lastFailure() {
        assertValid("last_failure", McpTools.lastFailure(FAILURE));
        assertValid("last_failure", McpTools.lastFailure(Optional.empty()));
    }

    @Test
    @DisplayName("diagnostics validates unfiltered, filtered, and filtered-to-nothing")
    void diagnostics() {
        assertValid("diagnostics", McpTools.diagnostics(DIAGS, null));
        assertValid("diagnostics", McpTools.diagnostics(DIAGS, "cart"));
        assertValid("diagnostics", McpTools.diagnostics(DIAGS, "nonesuch"));
    }

    @Test
    @DisplayName("rack_devices validates")
    void rackDevices() {
        assertValid("rack_devices", McpTools.rackDevices(List.of()));
    }

    @Test
    @DisplayName("ide_context validates — every emitted key is declared")
    void ideContext() {
        assertValid("ide_context", McpTools.ideContext(
                () -> new File("/tmp"), SERVINGS, List.of(), "/tmp/a.js", FAILURE, DIAGS));
        assertValid("ide_context", McpTools.ideContext(
                () -> null, List.of(), List.of(), null, Optional.empty(), Map.of()));
    }

    @Test
    @DisplayName("The validator itself rejects an undeclared key and a wrong type")
    void validatorBites() {
        JSONObject closed = McpTools.objectSchema(
                new JSONObject().put("a", new JSONObject().put("type", "integer")));
        assertThat(validate(closed, new JSONObject().put("a", 1).put("b", 2), "x"))
                .anyMatch(m -> m.contains("undeclared key"));
        assertThat(validate(closed, new JSONObject().put("a", "one"), "x"))
                .anyMatch(m -> m.contains("expected integer"));
        // a bare [] where a schema should be is caught as no-type-no-object
        assertThat(validate(new JSONObject().put("type", "array"), new JSONArray(), "y"))
                .isEmpty();
    }
}

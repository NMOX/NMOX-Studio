package org.nmox.studio.rack.mcp;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documentation that cannot lie (the DeviceDocsTest idiom, v2.82.0):
 * the Agent Port tutorial's rosters are gated against the code BOTH
 * ways — every production tool, catalogued resource, template and
 * prompt is named in docs/tutorials/agent-port.md, and every tool the
 * tutorial's table names exists. A tool added without its row, or a
 * row for a tool that was renamed, fails the build by name.
 */
class AgentPortDocsTest {

    private static String tutorial() throws Exception {
        File doc = new File("../docs/tutorials/agent-port.md");
        assertThat(doc).as("the tutorial exists beside the module").exists();
        return Files.readString(doc.toPath());
    }

    @Test
    @DisplayName("every tool, resource, template and prompt is named in the tutorial")
    void codeToDocs() throws Exception {
        String text = tutorial();
        McpTools tools = McpTools.production();
        List<String> missing = new ArrayList<>();
        for (McpTools.Tool t : tools.all()) {
            if (!text.contains("`" + t.name() + "`")) {
                missing.add("tool " + t.name());
            }
        }
        JSONArray resources = McpResources.list(tools).getJSONArray("resources");
        for (int i = 0; i < resources.length(); i++) {
            String uri = resources.getJSONObject(i).getString("uri");
            if (!text.contains("`" + uri + "`")) {
                missing.add("resource " + uri);
            }
        }
        JSONArray templates = McpResources.templates(tools).getJSONArray("resourceTemplates");
        for (int i = 0; i < templates.length(); i++) {
            String uri = templates.getJSONObject(i).getString("uriTemplate");
            if (!text.contains("`" + uri + "`")) {
                missing.add("template " + uri);
            }
        }
        JSONArray prompts = McpPrompts.list().getJSONArray("prompts");
        for (int i = 0; i < prompts.length(); i++) {
            String name = prompts.getJSONObject(i).getString("name");
            if (!text.contains("`" + name + "`")) {
                missing.add("prompt " + name);
            }
        }
        assertThat(missing).as("the tutorial names everything the port offers").isEmpty();
    }

    @Test
    @DisplayName("every tool the tutorial's table names exists, and the table is complete")
    void docsToCode() throws Exception {
        String text = tutorial();
        McpTools tools = McpTools.production();
        List<String> named = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^\\| `([a-z_]+)` \\|").matcher(text);
        while (m.find()) {
            named.add(m.group(1));
        }
        assertThat(named).as("the roster table has rows").isNotEmpty();
        List<String> unknown = new ArrayList<>();
        for (String n : named) {
            if (tools.byName(n) == null) {
                unknown.add(n);
            }
        }
        assertThat(unknown).as("a table row names a tool that does not exist").isEmpty();
        List<String> production = new ArrayList<>();
        for (McpTools.Tool t : tools.all()) {
            production.add(t.name());
        }
        assertThat(named).as("the table lists every tool, in the roster's order")
                .containsExactlyElementsOf(production);
        // the config snippet never carries a real token shape
        assertThat(text).doesNotContainPattern("Bearer [0-9a-f]{32,}");
        JSONObject sanity = new JSONObject().put("ok", true);
        assertThat(sanity.getBoolean("ok")).isTrue();
    }

    @Test
    @DisplayName("the official-client walk ships in scripts/ and exercises every primitive the port declares (v2.84.0)")
    void officialClientWalkShips() throws Exception {
        java.io.File script = new java.io.File("../scripts/agent-port-walk.mjs");
        assertThat(script).exists();
        String src = java.nio.file.Files.readString(script.toPath());
        for (String primitive : java.util.List.of("listTools(", "callTool(", "listResources(", "readResource(",
                "subscribeResource(", "listPrompts(", "getPrompt(", "complete(", "setLoggingLevel(",
                "ResourceUpdatedNotificationSchema", "LoggingMessageNotificationSchema")) {
            assertThat(src).as("the walk uses " + primitive).contains(primitive);
        }
        // the script reads the search answer by the schema's own field names —
        // the first live run read ".hits" and reported 0 for every query (v2.85.0)
        assertThat(src).as("search_text's structured answer is `matches`, not `hits`").contains("structuredContent?.matches").doesNotContain("structuredContent?.hits");
        assertThat(src).as("the token is never read off a command line").contains("NMOX_MCP_TOKEN").doesNotContain("process.argv[2] || process.env.NMOX_MCP_TOKEN");
        assertThat(java.nio.file.Files.readString(new java.io.File("../docs/tutorials/agent-port.md").toPath()))
                .as("the tutorial points at the shipped walk").contains("scripts/agent-port-walk.mjs");
        String node = org.nmox.studio.core.process.ToolLocator.resolve("node");
        org.junit.jupiter.api.Assumptions.assumeTrue(new java.io.File(node).isAbsolute() && new java.io.File(node).canExecute(), "node on PATH");
        Process p = new ProcessBuilder(node, "--check", script.getAbsolutePath()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(p.waitFor()).as("node --check: " + out).isZero();
    }
}

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
}

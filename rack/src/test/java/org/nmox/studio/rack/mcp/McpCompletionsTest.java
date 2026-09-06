package org.nmox.studio.rack.mcp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.SymbolIndex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** completion/complete (v2.84.0): bounded candidates for a prompt's argument or a template's slot; unknown references refuse. */
class McpCompletionsTest {

    private static SymbolIndex index(List<String> names, boolean truncated) {
        return new SymbolIndex() {
            @Override
            public Answer search(File root, String q, int limit) {
                List<Hit> hits = new ArrayList<>();
                for (String n : names) {
                    if (n.toLowerCase().startsWith(q.toLowerCase()) && hits.size() < limit) {
                        hits.add(new Hit(n, "FUNCTION", "src/a.js", 1));
                    }
                }
                return new Answer(hits, truncated);
            }

            @Override
            public Outline outline(File root, String file) {
                return new Outline(List.of(), "unused");
            }
        };
    }

    private static JSONObject prompt(String name, String arg, String value) {
        return new JSONObject()
                .put("ref", new JSONObject().put("type", "ref/prompt").put("name", name))
                .put("argument", new JSONObject().put("name", arg).put("value", value));
    }

    private static JSONObject resource(String uri, String arg, String value) {
        return new JSONObject()
                .put("ref", new JSONObject().put("type", "ref/resource").put("uri", uri))
                .put("argument", new JSONObject().put("name", arg).put("value", value));
    }

    private static List<Object> values(JSONObject result) {
        return result.getJSONObject("completion").getJSONArray("values").toList();
    }

    @Test
    @DisplayName("where_is's name completes from the symbol index, distinct, the index's order")
    void promptArgumentFromSymbols(@TempDir Path root) {
        McpCompletions c = new McpCompletions(
                index(List.of("checkout", "checkoutTotal", "checkout", "cart"), false), McpCompletions.rootOf(root));
        JSONObject r = c.complete(prompt("where_is", "name", "check"));
        assertThat(values(r)).containsExactly("checkout", "checkoutTotal");
        assertThat(r.getJSONObject("completion").getInt("total")).isEqualTo(2);
        assertThat(r.getJSONObject("completion").getBoolean("hasMore")).isFalse();
        assertThat(values(c.complete(prompt("where_is", "name", "  ")))).as("a blank value answers nothing").isEmpty();
    }

    @Test
    @DisplayName("values are capped at 100 with hasMore and the honest total")
    void cappedWithHasMore(@TempDir Path root) {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            many.add(String.format("sym%03d", i));
        }
        McpCompletions c = new McpCompletions(index(many, false), McpCompletions.rootOf(root));
        JSONObject completion = c.complete(prompt("where_is", "name", "sym")).getJSONObject("completion");
        assertThat(completion.getJSONArray("values").length()).isEqualTo(McpCompletions.MAX_VALUES);
        assertThat(completion.getBoolean("hasMore")).isTrue();
        assertThat(completion.has("total")).as("past the cap the index gives a floor, not a total — say nothing rather than 101 (v2.85.0)").isFalse();
        JSONObject few = c.complete(prompt("where_is", "name", "sym00")).getJSONObject("completion");
        assertThat(few.getInt("total")).as("under the cap the count is exact").isEqualTo(10);
        assertThat(few.getBoolean("hasMore")).isFalse();
    }

    @Test
    @DisplayName("the outline template's file completes from the project's files: prefix hits first, then contains, node_modules never")
    void templateFileFromProject(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src/app"));
        Files.createDirectories(root.resolve("node_modules/pkg"));
        Files.writeString(root.resolve("src/app/app.ts"), "");
        Files.writeString(root.resolve("src/app/shop.ts"), "");
        Files.writeString(root.resolve("README.md"), "");
        Files.writeString(root.resolve("node_modules/pkg/app.js"), "");
        McpCompletions c = new McpCompletions(index(List.of(), false), McpCompletions.rootOf(root));
        assertThat(values(c.complete(resource("nmox://outline/{file}", "file", "src/app/a"))))
                .containsExactly("src/app/app.ts");
        assertThat(values(c.complete(resource("nmox://outline/{file}", "file", "app"))))
                .as("contains-hits follow the prefix hits; the skipped dirs never complete")
                .containsExactly("src/app/app.ts", "src/app/shop.ts");
        assertThat(values(c.complete(resource("nmox://outline/{file}", "file", ""))))
                .as("an empty value lists every file, sorted").containsExactly("README.md", "src/app/app.ts", "src/app/shop.ts");
        assertThat(values(c.complete(resource("nmox://search/{query}", "query", "todo"))))
                .as("a search literal is anything — nothing to suggest").isEmpty();
    }

    @Test
    @DisplayName("unknown references and missing params refuse by name")
    void refusals(@TempDir Path root) {
        McpCompletions c = new McpCompletions(index(List.of(), false), McpCompletions.rootOf(root));
        assertThatThrownBy(() -> c.complete(prompt("nonesuch", "name", "x")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nonesuch");
        assertThatThrownBy(() -> c.complete(prompt("where_is", "file", "x")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("file");
        assertThatThrownBy(() -> c.complete(resource("nmox://outline/{file}", "query", "x")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("query");
        assertThatThrownBy(() -> c.complete(resource("nmox://nothing/{x}", "x", "x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.complete(new JSONObject().put("ref", new JSONObject().put("type", "ref/other"))
                .put("argument", new JSONObject().put("name", "a").put("value", "b"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ref.type");
        assertThatThrownBy(() -> c.complete(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.complete(new JSONObject())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("through the protocol: the capability is declared, a completion answers, a bad reference is -32602")
    void throughTheProtocol(@TempDir Path root) {
        McpTools tools = new McpTools(List.of());
        McpCompletions c = new McpCompletions(index(List.of("checkout"), false), McpCompletions.rootOf(root));
        String init = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}", tools, "2.84.0", null, c);
        assertThat(new JSONObject(init).getJSONObject("result").getJSONObject("capabilities").has("completions")).isTrue();
        String ok = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"completion/complete\",\"params\":"
                + prompt("where_is", "name", "che") + "}", tools, "2.84.0", null, c);
        JSONArray vals = new JSONObject(ok).getJSONObject("result").getJSONObject("completion").getJSONArray("values");
        assertThat(vals.toList()).containsExactly("checkout");
        String bad = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"completion/complete\",\"params\":"
                + prompt("nonesuch", "name", "che") + "}", tools, "2.84.0", null, c);
        assertThat(bad).contains("-32602").contains("nonesuch");
        String missing = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"completion/complete\"}", tools, "2.84.0", null, c);
        assertThat(missing).contains("-32602");
    }

    @Test
    @DisplayName("through the protocol: an outline instance subscribes when its file is inside the aim, -32002 outside or missing, -32602 past the cap")
    void fileSubscriptionThroughTheProtocol(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("app.js"), "x");
        McpTools tools = new McpTools(List.of(new McpTools.Tool("outline", "outline", "d",
                McpTools.objectSchema(new JSONObject()), McpTools.objectSchema(new JSONObject()),
                args -> new McpTools.ToolResult("", new JSONObject()))));
        McpCompletions c = new McpCompletions(index(List.of(), false), McpCompletions.rootOf(root));
        McpSubscriptions subs = new McpSubscriptions(60_000, 60_000);
        String ok = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/subscribe\",\"params\":{\"uri\":\"nmox://outline/app.js\"}}", tools, "2.84.0", subs, c);
        assertThat(ok).doesNotContain("error");
        assertThat(subs.isSubscribed("nmox://outline/app.js")).isTrue();
        String out = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"resources/subscribe\",\"params\":{\"uri\":\"nmox://outline/..%2F..%2Fetc%2Fpasswd\"}}", tools, "2.84.0", subs, c);
        assertThat(out).contains("-32002");
        String missing = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"resources/subscribe\",\"params\":{\"uri\":\"nmox://outline/none.js\"}}", tools, "2.84.0", subs, c);
        assertThat(missing).contains("-32002");
        String un = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"resources/unsubscribe\",\"params\":{\"uri\":\"nmox://outline/app.js\"}}", tools, "2.84.0", subs, c);
        assertThat(un).doesNotContain("error");
        assertThat(subs.isSubscribed("nmox://outline/app.js")).isFalse();
        assertThat(subs.watchedFiles()).isZero();
        String search = McpProtocol.handle("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"resources/subscribe\",\"params\":{\"uri\":\"nmox://search/todo\"}}", tools, "2.84.0", subs, c);
        assertThat(search).as("a search instance has nothing on disk to follow").contains("-32002");
        subs.close();
    }

    @Test
    @DisplayName("past the file walk's cap the file list is a floor: hasMore says so and no total is given (v2.85.0)")
    void fileListPastTheCapIsAFloor(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src"));
        for (int i = 0; i < TextSearch.MAX_FILES + 5; i++) {
            Files.writeString(root.resolve("src/f" + i + ".js"), "");
        }
        McpCompletions c = new McpCompletions(index(List.of(), false), McpCompletions.rootOf(root));
        JSONObject completion = c.complete(resource("nmox://outline/{file}", "file", "src/")).getJSONObject("completion");
        assertThat(completion.getJSONArray("values").length()).isEqualTo(McpCompletions.MAX_VALUES);
        assertThat(completion.getBoolean("hasMore")).isTrue();
        assertThat(completion.has("total")).as("a capped walk cannot count").isFalse();
        JSONObject narrow = c.complete(resource("nmox://outline/{file}", "file", "src/f1999")).getJSONObject("completion");
        assertThat(narrow.getJSONArray("values").length()).isLessThan(McpCompletions.MAX_VALUES);
        assertThat(narrow.getBoolean("hasMore")).as("few listed, but the walk was cut: more may exist").isTrue();
        assertThat(narrow.has("total")).isFalse();
    }
}

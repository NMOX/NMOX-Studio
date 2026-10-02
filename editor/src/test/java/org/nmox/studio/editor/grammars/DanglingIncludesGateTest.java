package org.nmox.studio.editor.grammars;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every rule a shipped grammar includes is defined somewhere (3.5.4).
 *
 * <p>Upstream grammars rename and delete rules and leave the includes
 * behind. The engine the editor runs logs a WARNING for each one whenever
 * the including rule is compiled, and a Markdown file's fenced blocks reach
 * a dozen grammars: seven such lines in the log of every session that opens
 * one, which is every session that opens an experiment.
 *
 * <p>{@code scripts/stub-dangling-grammar-includes.py} gives each such name
 * a rule that matches nothing, which is what the unresolved include already
 * was. This gate reads every shipped grammar, so a vendored grammar added
 * or bumped with a dangling include fails here and names it.
 */
class DanglingIncludesGateTest {

    private static final Path GRAMMARS = Path.of("src/main/resources/org/nmox/studio/editor/grammars");

    private static void includes(Object node, Set<String> out) {
        if (node instanceof JSONObject o) {
            if (o.opt("include") instanceof String target) {
                out.add(target);
            }
            for (String key : o.keySet()) {
                includes(o.get(key), out);
            }
        } else if (node instanceof JSONArray a) {
            for (int i = 0; i < a.length(); i++) {
                includes(a.get(i), out);
            }
        }
    }

    static List<String> dangling(Path directory) throws IOException {
        Map<String, JSONObject> grammars = new HashMap<>();
        Map<String, String> byScope = new HashMap<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                JSONObject g = new JSONObject(Files.readString(p, StandardCharsets.UTF_8));
                String name = p.getFileName().toString();
                grammars.put(name, g);
                if (g.opt("scopeName") instanceof String scope) {
                    byScope.put(scope, name);
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, JSONObject> e : grammars.entrySet()) {
            Set<String> found = new HashSet<>();
            includes(e.getValue(), found);
            for (String target : found) {
                String owner;
                String rule;
                if (target.startsWith("#")) {
                    owner = e.getKey();
                    rule = target.substring(1);
                } else if (target.contains("#")) {
                    owner = byScope.get(target.substring(0, target.indexOf('#')));
                    rule = target.substring(target.indexOf('#') + 1);
                    if (owner == null) {
                        continue; // a grammar this product does not ship
                    }
                } else {
                    continue;
                }
                JSONObject repository = grammars.get(owner).optJSONObject("repository");
                if (repository == null || !repository.has(rule)) {
                    out.add(e.getKey() + " includes " + target + ", which " + owner + " does not define");
                }
            }
        }
        out.sort(null);
        return out;
    }

    @Test
    @DisplayName("no shipped grammar includes a rule that is defined nowhere")
    void everyIncludedRuleExists() throws IOException {
        assertThat(dangling(GRAMMARS))
                .as("run scripts/stub-dangling-grammar-includes.py on the grammar directory: "
                        + "the engine logs a warning for each of these whenever its rule is compiled")
                .isEmpty();
    }

    @Test
    @DisplayName("the gate names an include of its own grammar's missing rule and of another's, and ignores a grammar we do not ship")
    void theGateBites(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.json"), """
                {"scopeName": "source.a",
                 "patterns": [{"include": "#here"}, {"include": "#gone"}, {"include": "source.b#there"},
                              {"include": "source.b#nowhere"}, {"include": "source.unshipped#x"}, {"include": "source.b"},
                              {"include": "$self"}],
                 "repository": {"here": {"match": "x"}}}
                """);
        Files.writeString(dir.resolve("b.json"), """
                {"scopeName": "source.b", "patterns": [], "repository": {"there": {"match": "y"}}}
                """);

        assertThat(dangling(dir)).containsExactly(
                "a.json includes #gone, which a.json does not define",
                "a.json includes source.b#nowhere, which b.json does not define");
    }

    @Test
    @DisplayName("a stub matches nothing: it has no pattern, no match, no begin")
    void aStubIsEmpty() throws IOException {
        int stubs = 0;
        try (Stream<Path> files = Files.list(GRAMMARS)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                JSONObject repository = new JSONObject(Files.readString(p, StandardCharsets.UTF_8))
                        .optJSONObject("repository");
                if (repository == null) {
                    continue;
                }
                for (String rule : repository.keySet()) {
                    if (repository.opt(rule) instanceof JSONObject r
                            && r.optString("comment").startsWith("NMOX stub:")) {
                        stubs++;
                        assertThat(r.keySet()).as(p.getFileName() + " #" + rule)
                                .containsExactlyInAnyOrder("comment", "patterns");
                        assertThat(r.getJSONArray("patterns").length()).isZero();
                    }
                }
            }
        }
        assertThat(stubs).as("the rules upstream grammars include and no longer define").isGreaterThanOrEqualTo(32);
    }
}

package org.nmox.studio.editor.grammars;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The capture-shape law (v1.200.1, from a live find): every value in a
 * {@code captures}/{@code beginCaptures}/{@code endCaptures}/{@code
 * whileCaptures} map must be a RULE OBJECT. Old TextMate-era grammars
 * carry two malformed shapes — a bare string ({@code "0": "scope"}) and
 * stray rule properties nested inside the map ({@code "end"/"name"} as
 * keys) — which vscode-textmate tolerates but TM4E does not: it throws
 * {@code ClassCastException} in {@code RawCaptures.getCapture}, corrupts
 * the rule table ("No rule with index 1 found") and kills highlighting
 * for EVERY grammar whose include graph touches the bad file. That is
 * how a mangled block in xml.tmLanguage.json (vendored v1.195.1 for the
 * embed scope) silently broke Nim: the nim grammar includes text.xml.
 * A vendor drop must fail THIS test, not a user's editor.
 */
class GrammarCapturesShapeTest {

    @Test
    @DisplayName("every vendored grammar's capture values are rule objects TM4E can compile")
    void allCaptureValuesAreRuleObjects() throws Exception {
        File dir = new File("src/main/resources/org/nmox/studio/editor/grammars");
        File[] grammars = dir.listFiles((d, n) -> n.endsWith(".json"));
        assertThat(grammars).as("grammar dir present").isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (File f : grammars) {
            if (f.getName().equals("package.json")) {
                continue;
            }
            JSONObject g = new JSONObject(Files.readString(f.toPath()));
            walk(g, f.getName(), "", violations);
        }
        assertThat(violations)
                .as("a non-object capture value crashes TM4E at tokenize time "
                        + "and takes every including grammar down with it")
                .isEmpty();
    }

    private static final String[] CAPTURE_KEYS = {
        "captures", "beginCaptures", "endCaptures", "whileCaptures"};

    private static void walk(Object o, String file, String path, List<String> out) {
        if (o instanceof JSONObject obj) {
            for (String key : CAPTURE_KEYS) {
                Object caps = obj.opt(key);
                if (caps instanceof JSONObject capsObj) {
                    for (String k : capsObj.keySet()) {
                        Object v = capsObj.get(k);
                        if (!(v instanceof JSONObject)) {
                            out.add(file + " " + path + "/" + key + "/" + k
                                    + " = " + String.valueOf(v));
                        }
                    }
                }
            }
            for (String k : obj.keySet()) {
                walk(obj.get(k), file, path + "/" + k, out);
            }
        } else if (o instanceof JSONArray arr) {
            for (int i = 0; i < arr.length(); i++) {
                walk(arr.get(i), file, path + "[" + i + "]", out);
            }
        }
    }
}

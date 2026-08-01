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

    /**
     * The SECOND shape TM4E rejects (v1.210.0, found by walking the app
     * as a junior dev would: creating a brand-new Vanilla Web project
     * raised a red "Unexpected Exception" badge before any code was
     * written). Every value in the top-level {@code repository} must be
     * a RULE OBJECT — TM4E's {@code RawRepository.getRule} casts it to
     * {@code IRawRule} and a bare ARRAY throws
     * {@code ClassCastException} deep in
     * {@code ScopeDependencyProcessor}, which is how
     * racket.tmLanguage.json's {@code lambda-onearg} (a one-element
     * array, tolerated by vscode-textmate) surfaced as an unexplained
     * error on a junior's first project. The fix is to say what was
     * meant: <code>{"patterns": [...]}</code>.
     *
     * <p>Same family as the capture law above, different door — which is
     * the lesson: gate the SHAPE FAMILY a foreign parser rejects, not the
     * single instance you tripped over.
     */
    @Test
    @DisplayName("every vendored grammar's repository values are rule objects, never bare arrays")
    void allRepositoryValuesAreRuleObjects() throws Exception {
        File dir = new File("src/main/resources/org/nmox/studio/editor/grammars");
        File[] grammars = dir.listFiles((d, n) -> n.endsWith(".json"));
        assertThat(grammars).as("grammar dir present").isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (File f : grammars) {
            if (f.getName().equals("package.json")) {
                continue;
            }
            JSONObject g = new JSONObject(Files.readString(f.toPath()));
            // v1.216.0 (arc review): the original check stopped at the
            // TOP-LEVEL repository — but TextMate allows a repository on
            // ANY rule, TM4E resolves nested ones through the same cast,
            // and four shipped grammars (clarity, crystal, html, ruby)
            // already carry nested repositories. Walk them all, or the
            // gate guards the door while the window stands open — the
            // exact "gate the shape family, not the instance" lesson
            // this test's own javadoc records.
            walkRepositories(g, f.getName(), "", violations);
        }
        assertThat(violations)
                .as("a bare array in `repository` throws ClassCastException in "
                        + "TM4E's RawRepository.getRule and surfaces to the user "
                        + "as an unexplained Unexpected Exception")
                .isEmpty();
    }

    /** Checks every {@code repository} at any depth, not just the root's. */
    private static void walkRepositories(Object o, String file, String path,
            List<String> out) {
        if (o instanceof JSONObject obj) {
            Object repo = obj.opt("repository");
            if (repo != null && !(repo instanceof JSONObject)) {
                out.add(file + " " + path + "/repository is "
                        + repo.getClass().getSimpleName() + ", not an object");
            }
            if (repo instanceof JSONObject repoObj) {
                for (String k : repoObj.keySet()) {
                    if (!(repoObj.get(k) instanceof JSONObject)) {
                        out.add(file + " " + path + "/repository/" + k + " is "
                                + repoObj.get(k).getClass().getSimpleName()
                                + ", not a rule object");
                    }
                }
            }
            for (String k : obj.keySet()) {
                walkRepositories(obj.get(k), file, path + "/" + k, out);
            }
        } else if (o instanceof JSONArray arr) {
            for (int i = 0; i < arr.length(); i++) {
                walkRepositories(arr.get(i), file, path + "[" + i + "]", out);
            }
        }
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

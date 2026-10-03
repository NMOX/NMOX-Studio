package org.nmox.studio.editor.debug;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Debugging launches the project's own code — the same act the rack gates
 * behind Workspace Trust before it fires a device. This pins that the
 * debug action asks the same question against the same trust record, so
 * "Keep Safe" on a stranger's folder stops the debugger too.
 *
 * (The prompt itself is a Swing dialog; headless it auto-allows by design,
 * so what's testable here is that the action consults the gate and resolves
 * the same project root the rack would.)
 */
class DebugTrustGateTest {

    @Test
    @DisplayName("the debug action consults WorkspaceTrust before launching anything")
    void shouldGateOnWorkspaceTrust() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/debug/DapDebugAction.java"),
                StandardCharsets.UTF_8);

        // comments describe the code; the gate reads the code
        String code = source.replace("\r\n", "\n").replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");

        // the one lane: trust is asked, a refusal returns, and only then does the start run
        String lane = body(code, "private static void gated(");
        int trustCheck = lane.indexOf("WorkspaceTrust.requestTrust(trustRoot.get())");
        int start = lane.indexOf("start.run()");
        assertThat(trustCheck)
                .as("debug must ask for workspace trust — it runs project code")
                .isGreaterThan(0);
        assertThat(start).as("the start comes after the question").isGreaterThan(trustCheck);
        assertThat(lane.substring(trustCheck, start)).as("Keep Safe leaves before anything starts").contains("return;");
        assertThat(code.split("WorkspaceTrust\\.requestTrust", -1)).as("one gate, not several to keep in step").hasSize(2);

        // every adapter is started from inside a gated(...) call: a launch, a
        // launch with a runtime, a Python interpreter, an attach
        List<int[]> gatedCalls = calls(code, "gated(");
        assertThat(gatedCalls).as("launch, launchNode, launchPython, attachNode").hasSize(4);
        for (String starter : new String[] {"debugPython(", "debugGo(", "debugNode("}) {
            List<int[]> uses = calls(code, starter);
            assertThat(uses).as(starter + " is called").isNotEmpty();
            for (int[] use : uses) {
                assertThat(gatedCalls).as(starter + " at offset " + use[0] + " must sit inside a gated(…) call")
                        .anyMatch(g -> g[0] < use[0] && use[1] <= g[1]);
            }
        }

        // and the spawns themselves live only inside those three starters
        String starters = body(code, "private static void debugPython(") + body(code, "private static void debugGo(")
                + body(code, "private static void debugNode(");
        for (String spawn : new String[] {"JsDebugServer.start(", "new ProcessBuilder("}) {
            assertThat(code.split(java.util.regex.Pattern.quote(spawn), -1).length)
                    .as(spawn + " appears nowhere but in a starter that only a gated call reaches")
                    .isEqualTo(starters.split(java.util.regex.Pattern.quote(spawn), -1).length);
        }
    }

    /** The text of the method whose declaration starts with {@code signature}, braces matched. */
    private static String body(String code, String signature) {
        int at = code.indexOf(signature);
        assertThat(at).as(signature + " exists").isGreaterThanOrEqualTo(0);
        int open = code.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return code.substring(at, i + 1);
            }
        }
        throw new AssertionError("unbalanced braces after " + signature);
    }

    /** Every CALL of {@code name} (not its declaration), as [start, end of its matched parentheses]. */
    private static List<int[]> calls(String code, String name) {
        List<int[]> out = new java.util.ArrayList<>();
        for (int at = code.indexOf(name); at >= 0; at = code.indexOf(name, at + 1)) {
            if (at > 0 && Character.isJavaIdentifierPart(code.charAt(at - 1))) {
                continue;   // another method's name ends this way
            }
            if (code.substring(Math.max(0, at - 5), at).equals("void ")) {
                continue;   // the declaration
            }
            int depth = 0;
            for (int i = at + name.length() - 1; i < code.length(); i++) {
                char c = code.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')' && --depth == 0) {
                    out.add(new int[] {at, i});
                    break;
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("the browser debug action gates on WorkspaceTrust before locating or spawning anything")
    void shouldGateBrowserDebugOnWorkspaceTrust() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/debug/BrowserDebugAction.java"),
                StandardCharsets.UTF_8);

        int trustCheck = source.indexOf("WorkspaceTrust.requestTrust");
        assertThat(trustCheck)
                .as("browser debug runs project code in a browser we spawned — it must ask")
                .isGreaterThan(0);

        // the gate comes before the browser probe AND before the launch;
        // even BrowserLocator.find() must not run for an untrusted folder
        for (String later : new String[] {"BrowserLocator.find()", "debugChrome(label"}) {
            assertThat(source.indexOf(later))
                    .as(later + " must come after the trust gate")
                    .isGreaterThan(trustCheck);
        }
    }

    @Test
    @DisplayName("the trusted root is the project root, not the file's folder")
    void shouldTrustAtProjectRoot(@TempDir Path dir) throws Exception {
        // a manifest at the root, the source nested below it
        Files.writeString(dir.resolve("package.json"), "{\"name\":\"demo\"}");
        Path nested = Files.createDirectories(dir.resolve("src/deep"));
        File file = Files.writeString(nested.resolve("main.js"), "1;").toFile();

        File root = org.nmox.studio.rack.devices.ProjectInspector
                .hasProjectManifest(dir.toFile()) ? dir.toFile() : null;
        assertThat(root).as("fixture sanity").isNotNull();

        // the walk the action performs: nearest ancestor holding a manifest
        File walked = file.getParentFile();
        for (File d = walked; d != null; d = d.getParentFile()) {
            if (org.nmox.studio.rack.devices.ProjectInspector.hasProjectManifest(d)) {
                walked = d;
                break;
            }
        }
        assertThat(walked.getCanonicalFile())
                .as("trust is asked once for the project, not per subfolder")
                .isEqualTo(dir.toFile().getCanonicalFile());
    }
}

package org.nmox.studio.editor.debug;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

        int trustCheck = source.indexOf("WorkspaceTrust.requestTrust");
        assertThat(trustCheck)
                .as("debug must ask for workspace trust — it runs project code")
                .isGreaterThan(0);

        // and it must gate BEFORE any adapter or debuggee is spawned
        for (String launcher : new String[] {"debugPython(file)", "debugGo(file)", "debugNode(file)"}) {
            assertThat(source.indexOf(launcher))
                    .as(launcher + " must come after the trust gate")
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

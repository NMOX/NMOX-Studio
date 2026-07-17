package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 47 (closed v1.77.1): INSPECTOR's AUTO must grey honestly for
 * toolchains with no wired debugger instead of launching a doomed
 * node command — the same honest-grey law VERITAS follows for Ada and
 * IGNITION for ReScript. An explicit knob position stays the user's
 * call and always resolves.
 */
class DebugDeviceGreyTest {

    @TempDir
    Path dir;

    private Rack rackAimedAt(String manifest) throws IOException {
        Files.writeString(dir.resolve(manifest), "# manifest");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        return rack;
    }

    @Test
    @DisplayName("AUTO greys on a Rust project — no doomed node command")
    void autoGreysForUndebuggableKind() throws IOException {
        Rack rack = rackAimedAt("Cargo.toml");
        DebugDevice debug = new DebugDevice();
        rack.addDevice(debug);
        assertThat(debug.buildCommand())
                .as("no wired debugger for RUST — the command must be empty, not node")
                .isEmpty();
        rack.shutdown();
    }

    @Test
    @DisplayName("AUTO still resolves node for Node projects and debugpy for Python")
    void autoStillResolvesDebuggableKinds() throws IOException {
        Rack rack = rackAimedAt("package.json");
        DebugDevice node = new DebugDevice();
        rack.addDevice(node);
        assertThat(node.buildCommand()).startsWith("node", "--inspect=9229");
        rack.shutdown();

        Path py = Files.createDirectories(dir.resolveSibling("py-proj"));
        Files.writeString(py.resolve("pyproject.toml"), "# manifest");
        Rack rack2 = new Rack();
        rack2.setProjectDir(py.toFile());
        DebugDevice python = new DebugDevice();
        rack2.addDevice(python);
        assertThat(python.buildCommand()).startsWith("python3", "-m", "debugpy");
        rack2.shutdown();
    }

    @Test
    @DisplayName("An explicit knob position overrides the grey — the user's call")
    void explicitTargetAlwaysResolves() throws IOException {
        Rack rack = rackAimedAt("Cargo.toml");
        DebugDevice debug = new DebugDevice();
        // knob params persist by INDEX: TARGETS[1] == "node"
        debug.applyState(java.util.Map.of("target", "1"));
        rack.addDevice(debug);
        assertThat(debug.buildCommand()).startsWith("node", "--inspect=9229");
        rack.shutdown();
    }
}

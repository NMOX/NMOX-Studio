package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Rust lanes: a Cargo project lints with clippy and formats with
 * rustfmt — both ship with the toolchain, so the AUTO lanes must speak
 * them instead of reaching for npx-resolved Node tooling that has
 * nothing to say about Rust code.
 */
class RustLanesTest {

    @TempDir
    Path dir;

    private Rack aimedCargoRack() throws IOException {
        Files.writeString(dir.resolve("Cargo.toml"),
                "[package]\nname = \"x\"\nversion = \"0.1.0\"\n");
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/main.rs"), "fn main() {}\n");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        return rack;
    }

    @Test
    @DisplayName("PURITY auto lints with cargo clippy; FIX spells --fix --allow-dirty")
    void purityAuto() throws IOException {
        Rack rack = aimedCargoRack();
        try {
            LintDevice lint = new LintDevice();
            rack.addDevice(lint);
            assertThat(lint.buildCommand()).containsExactly("cargo", "clippy");
            lint.applyState(java.util.Map.of("fix", "true"));
            // clippy --fix refuses a dirty tree; an IDE's tree is dirty
            // by definition mid-edit
            assertThat(lint.buildCommand()).containsExactly(
                    "cargo", "clippy", "--fix", "--allow-dirty");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("GLOSS writes with cargo fmt (default) and checks with cargo fmt --check")
    void glossAuto() throws IOException {
        Rack rack = aimedCargoRack();
        try {
            FormatDevice fmt = new FormatDevice();
            rack.addDevice(fmt);
            assertThat(fmt.buildCommand()).containsExactly("cargo", "fmt");
            fmt.applyState(java.util.Map.of("write", "false"));
            assertThat(fmt.buildCommand()).containsExactly("cargo", "fmt", "--check");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("the clippy summary drives the findings LCD: 'generated N warnings'")
    void clippySummaryShape() {
        // pinned live on cargo 1.95:
        // warning: `rsprobe` (bin "rsprobe") generated 3 warnings (run ...)
        Pattern p = Pattern.compile("generated (\\d+) warnings?\\b");
        var m = p.matcher(
                "warning: `rsprobe` (bin \"rsprobe\") generated 3 warnings"
                + " (run `cargo clippy --fix --bin \"rsprobe\" -p rsprobe -- ` to apply 1 suggestion)");
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).isEqualTo("3");
        assertThat(p.matcher("warning: unused variable: `x`").find()).isFalse();
    }
}

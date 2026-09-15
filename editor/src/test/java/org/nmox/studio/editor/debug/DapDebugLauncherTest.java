package org.nmox.studio.editor.debug;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.DebugLauncher;
import org.openide.util.Lookup;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The editor's {@link DebugLauncher}: published in the default Lookup, and
 * deciding support by the same four MIME types the right-click action
 * registers for — so the platform's Debug File row can never offer a file
 * the debugger would refuse, nor refuse one it would take.
 */
class DapDebugLauncherTest {

    @Test
    @DisplayName("the editor publishes the launcher the project layer looks up")
    void shouldBePublishedInLookup() {
        DebugLauncher found = Lookup.getDefault().lookup(DebugLauncher.class);
        assertThat(found).isInstanceOf(DapDebugLauncher.class);
        // surefire runs with the module directory as cwd
        assertThat(new File("target/classes/META-INF/services/org.nmox.studio.core.spi.DebugLauncher"))
                .as("the @ServiceProvider registration is generated").exists();
    }

    @Test
    @DisplayName("support follows the action's four MIME types, no more")
    void shouldSupportExactlyTheActionsMimes() {
        assertThat(DapDebugAction.supportsMime("text/javascript")).isTrue();
        assertThat(DapDebugAction.supportsMime("text/typescript")).isTrue();
        assertThat(DapDebugAction.supportsMime("text/x-python")).isTrue();
        assertThat(DapDebugAction.supportsMime("text/x-go")).isTrue();
        assertThat(DapDebugAction.supportsMime("text/html")).isFalse();
        assertThat(DapDebugAction.supportsMime("text/x-java")).isFalse();
        assertThat(DapDebugAction.supportsMime(null)).isFalse();
    }

    @Test
    @DisplayName("a file's type comes from the platform when it can say, else from the extension table")
    void shouldResolveFilesByMimeOrExtension(@TempDir Path tmp) throws Exception {
        DapDebugLauncher launcher = new DapDebugLauncher();
        assertThat(launcher.supports(Files.writeString(tmp.resolve("a.js"), "1").toFile())).isTrue();
        assertThat(launcher.supports(Files.writeString(tmp.resolve("b.ts"), "1").toFile())).isTrue();
        assertThat(launcher.supports(Files.writeString(tmp.resolve("c.py"), "1").toFile())).isTrue();
        assertThat(launcher.supports(Files.writeString(tmp.resolve("d.go"), "1").toFile())).isTrue();
        assertThat(launcher.supports(Files.writeString(tmp.resolve("e.html"), "1").toFile())).isFalse();
        assertThat(launcher.supports(Files.writeString(tmp.resolve("Makefile"), "1").toFile())).isFalse();
        assertThat(launcher.supports(tmp.resolve("missing.js").toFile()))
                .as("a file that does not exist still answers by extension; the launch refuses later").isTrue();
    }
}

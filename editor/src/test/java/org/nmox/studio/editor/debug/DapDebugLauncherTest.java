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

    @Test
    @DisplayName("a working directory is honoured for Node and Python only; Go's delve takes the directory as its program")
    void shouldHonourAWorkingDirectoryOnlyWhereTheAdapterTakesOne(@TempDir Path tmp) throws Exception {
        assertThat(DapDebugAction.supportsWorkingDir("text/javascript")).isTrue();
        assertThat(DapDebugAction.supportsWorkingDir("text/typescript")).isTrue();
        assertThat(DapDebugAction.supportsWorkingDir("text/x-python")).isTrue();
        assertThat(DapDebugAction.supportsWorkingDir("text/x-go")).isFalse();
        assertThat(DapDebugAction.supportsWorkingDir(null)).isFalse();

        // the refusals return false having started nothing — no launch is
        // posted for these, so nothing here needs a debugger to exist
        DapDebugLauncher launcher = new DapDebugLauncher();
        File dir = tmp.toFile();
        assertThat(launcher.debug(Files.writeString(tmp.resolve("m.go"), "1").toFile(), dir))
                .as("Go with a working directory is not offered").isFalse();
        assertThat(launcher.debug(Files.writeString(tmp.resolve("p.html"), "1").toFile(), dir)).isFalse();
        assertThat(launcher.debug(tmp.resolve("a.js").toFile(), null)).isFalse();
        assertThat(launcher.debugPage(" ", dir)).isFalse();
        assertThat(launcher.debugPage("http://localhost:1", null)).isFalse();
    }

    @Test
    @DisplayName("the facade's additive doors default to 'not offered', so an older launcher refuses rather than guesses")
    void facadeDefaultsRefuse() {
        DebugLauncher bare = new DebugLauncher() {
            @Override
            public boolean supports(File file) {
                return true;
            }

            @Override
            public void debug(File file) {
                throw new AssertionError("the working-directory door must not fall back to the plain one");
            }
        };
        assertThat(bare.debug(new File("a.js"), new File("."))).isFalse();
        assertThat(bare.debugPage("http://localhost:1", new File("."))).isFalse();
    }
}

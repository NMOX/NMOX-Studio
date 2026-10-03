package org.nmox.studio.editor.debug;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
        File here = new File(".");
        assertThat(bare.debug(new DebugLauncher.Launch(DebugLauncher.Language.NODE, "n", new File("a.js"), here, here,
                List.of(), Map.of(), "tsx", List.of()))).as("a runtime it was never taught").isFalse();
        assertThat(bare.debug(new DebugLauncher.Launch(DebugLauncher.Language.NODE, "n", new File("a.js"), here, here,
                List.of(), Map.of(), null, List.of("--inspect")))).isFalse();
        assertThat(bare.debug(new DebugLauncher.Launch(DebugLauncher.Language.NODE, "n", null, here, here,
                List.of(), Map.of(), "npm", List.of("run", "dev")))).isFalse();
        assertThat(bare.attachNode("a", "localhost", 9229, here)).isFalse();
    }

    @Test
    @DisplayName("a launch that names a runtime is refused, having started nothing, when it is not one this launcher can start exactly")
    void runtimeLaunchesItCannotStartExactly(@TempDir Path tmp) throws Exception {
        DapDebugLauncher launcher = new DapDebugLauncher();
        File dir = tmp.toFile();
        File py = Files.writeString(tmp.resolve("main.py"), "1").toFile();
        File js = Files.writeString(tmp.resolve("a.js"), "1").toFile();
        assertThat(launcher.debug((DebugLauncher.Launch) null)).isFalse();
        assertThat(launcher.debug(new DebugLauncher.Launch(DebugLauncher.Language.NODE, "n", py, dir, dir,
                List.of(), Map.of(), "tsx", List.of()))).as("a Node runtime does not run a .py").isFalse();
        assertThat(launcher.debug(new DebugLauncher.Launch(DebugLauncher.Language.PYTHON, "p", js, dir, dir,
                List.of(), Map.of(), "python3.12", List.of()))).as("an interpreter does not run a .js").isFalse();
        assertThat(launcher.debug(new DebugLauncher.Launch(DebugLauncher.Language.PYTHON, "p", py, dir, dir,
                List.of(), Map.of(), "python3.12", List.of("-X", "dev"))))
                .as("interpreter arguments are not passed on, so the launch is not started without them").isFalse();
        assertThat(launcher.debug(new DebugLauncher.Launch(DebugLauncher.Language.PYTHON, "p", null, dir, dir,
                List.of(), Map.of(), "python3.12", List.of()))).as("Python needs its program").isFalse();
    }

    @Test
    @DisplayName("an attach is to this machine or to nothing: the launcher refuses what the reader should already have refused")
    void attachOnlyToLoopback(@TempDir Path tmp) {
        DapDebugLauncher launcher = new DapDebugLauncher();
        File dir = tmp.toFile();
        // literal addresses only: a host NAME here would be looked up on the network the day this guard broke
        for (String address : new String[] {"10.0.0.5", "192.168.1.20", "0.0.0.0", "", "127.0.0.2", null}) {
            assertThat(launcher.attachNode("a", address, 9229, dir)).as(String.valueOf(address)).isFalse();
        }
        assertThat(launcher.attachNode("a", "localhost", 0, dir)).isFalse();
        assertThat(launcher.attachNode("a", "localhost", 65536, dir)).isFalse();
        assertThat(launcher.attachNode("a", "localhost", 9229, null)).isFalse();
        assertThat(launcher.attachNode(null, "localhost", 9229, dir)).isFalse();

        assertThat(DebugLauncher.isLoopback("localhost")).isTrue();
        assertThat(DebugLauncher.isLoopback("LOCALHOST")).isTrue();
        assertThat(DebugLauncher.isLoopback("127.0.0.1")).isTrue();
        assertThat(DebugLauncher.isLoopback("::1")).isTrue();
        assertThat(DebugLauncher.isLoopback("localhost.evil.example")).isFalse();
    }

    @Test
    @DisplayName("a Launch copies what it is given, calls a launch with no runtime plain, and prints no variable's value")
    void launchValue() {
        File here = new File(".");
        java.util.List<String> args = new java.util.ArrayList<>(List.of("--port", "3000"));
        DebugLauncher.Launch launch = new DebugLauncher.Launch(DebugLauncher.Language.NODE, "n", new File("a.js"),
                here, here, args, Map.of("API_TOKEN", "hunter2"), null, List.of());
        args.add("later");
        assertThat(launch.args()).containsExactly("--port", "3000");
        assertThat(launch.plain()).isTrue();
        assertThat(launch.toString()).contains("API_TOKEN").doesNotContain("hunter2");
        assertThat(new DebugLauncher.Launch(DebugLauncher.Language.NODE, "n", new File("a.js"), here, here,
                List.of(), Map.of(), null, List.of("--inspect")).plain()).isFalse();
    }
}

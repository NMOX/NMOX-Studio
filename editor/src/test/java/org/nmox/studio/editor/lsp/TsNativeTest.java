package org.nmox.studio.editor.lsp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.lsp.TsNative.Choice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TypeScript 7 is served by its own server, found without Node, and the
 * project's copy of it waits for trust (3.10.0).
 *
 * <p>The layouts below are what {@code npm install typescript@7.0.2} left
 * on disk in November 2026: {@code node_modules/typescript} holding a
 * launcher and no {@code lib/tsserver.js}, and the compiler at
 * {@code node_modules/@typescript/typescript-darwin-arm64/lib/tsc}.
 */
class TsNativeTest {

    private static final String PKG = "typescript-linux-x64";

    private static Path typescript7(Path nodeModules) throws Exception {
        Path ts = nodeModules.resolve("typescript");
        Files.createDirectories(ts.resolve("bin"));
        Files.createDirectories(ts.resolve("lib"));
        Files.writeString(ts.resolve("package.json"), "{\"name\":\"typescript\",\"version\":\"7.0.2\"}");
        Files.writeString(ts.resolve("bin/tsc"), "#!/usr/bin/env node\nimport \"../lib/tsc.js\";\n");
        return ts;
    }

    private static Path compiler(Path nodeModules, String name) throws Exception {
        Path exe = nodeModules.resolve("@typescript/" + PKG + "/lib/" + name);
        Files.createDirectories(exe.getParent());
        Files.writeString(exe, "the compiler");
        exe.toFile().setExecutable(true);
        return exe;
    }

    @Test
    @DisplayName("the package that holds the compiler is named the way npm names it for the machine")
    void platformPackage() {
        assertThat(TsNative.platformPackage("Mac OS X", "aarch64")).isEqualTo("typescript-darwin-arm64");
        assertThat(TsNative.platformPackage("Mac OS X", "x86_64")).isEqualTo("typescript-darwin-x64");
        assertThat(TsNative.platformPackage("Linux", "amd64")).isEqualTo("typescript-linux-x64");
        assertThat(TsNative.platformPackage("Linux", "aarch64")).isEqualTo("typescript-linux-arm64");
        assertThat(TsNative.platformPackage("Windows 11", "amd64")).isEqualTo("typescript-win32-x64");
        assertThat(TsNative.platformPackage("Windows 11", "aarch64")).isEqualTo("typescript-win32-arm64");
        assertThat(TsNative.platformPackage("Darwin", "arm64")).as("'Darwin' holds the letters w-i-n").isEqualTo("typescript-darwin-arm64");
        assertThat(TsNative.platformPackage("FreeBSD", "amd64")).isEqualTo("typescript-freebsd-x64");
    }

    @Test
    @DisplayName("npm's layout: the compiler is beside the package, and is named by a path inside the project")
    void besideThePackage(@TempDir Path project) throws Exception {
        Path ts = typescript7(project.resolve("node_modules"));
        assertThat(TsNative.exe(ts.toFile(), PKG, false)).as("no compiler for this machine was installed").isNull();
        Path exe = compiler(project.resolve("node_modules"), "tsc");
        File found = TsNative.exe(ts.toFile(), PKG, false);
        assertThat(found).isEqualTo(exe.toFile());
        assertThat(found.getPath()).startsWith(project.toFile().getPath());
        assertThat(TsNative.exe(ts.toFile(), "typescript-darwin-arm64", false))
                .as("another machine's compiler is not this one's").isNull();
        assertThat(TsNative.exe(ts.toFile(), PKG, true)).as("on Windows it is tsc.exe").isNull();
        Path windows = compiler(project.resolve("node_modules"), "tsc.exe");
        assertThat(TsNative.exe(ts.toFile(), PKG, true)).isEqualTo(windows.toFile());
        assertThat(TsNative.exe(null, PKG, false)).isNull();
    }

    @Test
    @DisplayName("a compiler nested inside the package is found too, and a directory of that name is not a compiler")
    void nestedAndNotADirectory(@TempDir Path project) throws Exception {
        Path ts = typescript7(project.resolve("node_modules"));
        Files.createDirectories(project.resolve("node_modules/@typescript/" + PKG + "/lib/tsc"));
        assertThat(TsNative.exe(ts.toFile(), PKG, false)).as("a directory is executable; it is not a file").isNull();
        Path nested = compiler(ts.resolve("node_modules"), "tsc");
        assertThat(TsNative.exe(ts.toFile(), PKG, false)).isEqualTo(nested.toFile());
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "every file is executable there, and a link needs a privilege")
    @DisplayName("a file that cannot be run is not the compiler, and pnpm's linked package is followed to its store")
    void notExecutableAndLinked(@TempDir Path project) throws Exception {
        Path ts = typescript7(project.resolve("node_modules"));
        Path exe = compiler(project.resolve("node_modules"), "tsc");
        exe.toFile().setExecutable(false);
        assertThat(TsNative.exe(ts.toFile(), PKG, false)).isNull();

        Path store = project.resolve("pnpm/node_modules/.pnpm/typescript@7.0.2/node_modules");
        Path real = typescript7(store);
        Path stored = compiler(store, "tsc");
        Path link = project.resolve("pnpm/node_modules/typescript");
        Files.createSymbolicLink(link, real);
        File found = TsNative.exe(link.toFile(), PKG, false);
        assertThat(found).as("seen from the package's real path").isNotNull();
        assertThat(found.getCanonicalFile()).isEqualTo(stored.toFile().getCanonicalFile());
    }

    @Test
    @DisplayName("the decision: the project's compiler runs only when trusted, the user's own serves until then")
    void choose() {
        // found, fromWorkspace, trusted, own
        assertThat(TsNative.choose(true, true, true, false)).isEqualTo(Choice.FOUND);
        assertThat(TsNative.choose(true, true, true, true)).as("a trusted project is served by the version it pinned").isEqualTo(Choice.FOUND);
        assertThat(TsNative.choose(true, true, false, true)).as("not trusted: the user's own, not the project's").isEqualTo(Choice.OWN);
        assertThat(TsNative.choose(true, true, false, false)).as("not trusted, and nothing else to run").isEqualTo(Choice.WAIT_FOR_TRUST);
        assertThat(TsNative.choose(true, false, false, false)).as("beside the user's own tools: no project brought it").isEqualTo(Choice.FOUND);
        assertThat(TsNative.choose(true, false, false, true)).isEqualTo(Choice.FOUND);
        assertThat(TsNative.choose(true, false, true, false)).isEqualTo(Choice.FOUND);
        assertThat(TsNative.choose(false, true, true, true)).as("the project's has no compiler for this machine").isEqualTo(Choice.OWN);
        assertThat(TsNative.choose(false, true, false, true)).isEqualTo(Choice.OWN);
        assertThat(TsNative.choose(false, true, true, false)).as("trust does not make a missing compiler appear").isEqualTo(Choice.NONE);
        assertThat(TsNative.choose(false, true, false, false)).isEqualTo(Choice.NONE);
        assertThat(TsNative.choose(false, false, false, false)).isEqualTo(Choice.NONE);
        assertThat(TsNative.choose(false, false, true, true)).isEqualTo(Choice.OWN);
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "npm links the launcher on POSIX; the Windows layout is its own test")
    @DisplayName("the user's own compiler is the one the tsc on the PATH belongs to: TypeScript 7 only, and never the project's")
    void ownCompiler(@TempDir Path prefix, @TempDir Path project) throws Exception {
        String pkg = TsNative.platformPackage();
        Path ts = typescript7(prefix.resolve("lib/node_modules"));
        Path exe = prefix.resolve("lib/node_modules/@typescript/" + pkg + "/lib/tsc");
        Files.createDirectories(exe.getParent());
        Files.writeString(exe, "the compiler");
        exe.toFile().setExecutable(true);
        Files.createDirectories(prefix.resolve("bin"));
        Path launcher = prefix.resolve("bin/tsc");
        Files.createSymbolicLink(launcher, ts.resolve("bin/tsc"));

        File own = TsNative.ownExe(launcher.toFile(), project.toFile());
        assertThat(own).isNotNull();
        assertThat(own.getCanonicalFile()).isEqualTo(exe.toFile().getCanonicalFile());
        assertThat(TsNative.ownExe(launcher.toFile(), null)).as("a file in no project").isNotNull();
        assertThat(TsNative.ownExe(null, project.toFile())).isNull();
        assertThat(TsNative.ownExe(new File("tsc"), project.toFile())).as("an unresolved name is nobody's").isNull();
        assertThat(TsNative.ownExe(launcher.toFile(), prefix.toFile()))
                .as("a tsc that resolves into the project is the project's: node_modules/.bin on the PATH").isNull();

        Files.writeString(ts.resolve("lib/tsserver.js"), "// TypeScript 5 and 6 have this, and no --lsp");
        assertThat(TsNative.ownExe(launcher.toFile(), project.toFile())).isNull();
    }

    @Test
    @DisplayName("a launcher that is its own script (npm on Windows) names its package by where it stands")
    void ownCompilerByLayout(@TempDir Path prefix) throws Exception {
        Path ts = typescript7(prefix.resolve("node_modules"));
        Path launcher = prefix.resolve("tsc.cmd");
        Files.writeString(launcher, "@node node_modules\\typescript\\bin\\tsc %*");
        assertThat(TsNative.packageOf(launcher.toFile())).isEqualTo(ts.toFile());
        assertThat(TsNative.packageOf(prefix.resolve("elsewhere/bin/tsc").toFile())).isNull();
    }

    @Test
    @DisplayName("inside means under the directory, by the path as written or by the real one, and never a sibling with a longer name")
    void inside(@TempDir Path root) throws Exception {
        File project = root.resolve("shop").toFile();
        assertThat(TsNative.inside(new File(project, "node_modules/x/tsc"), project)).isTrue();
        assertThat(TsNative.inside(project, project)).isTrue();
        assertThat(TsNative.inside(root.resolve("shop-two/tsc").toFile(), project)).isFalse();
        assertThat(TsNative.inside(root.resolve("elsewhere/tsc").toFile(), project)).isFalse();
        assertThat(TsNative.inside(new File(project, "tsc"), null)).as("no project: nothing is the project's").isFalse();
        assertThat(TsNative.inside(null, project)).isFalse();
    }

    @Test
    @DisplayName("the command is the compiler itself, as a language server on standard streams")
    void command(@TempDir Path dir) {
        File exe = dir.resolve("tsc").toFile();
        assertThat(TsNative.command(exe)).containsExactly(exe.getAbsolutePath(), "--lsp", "--stdio");
    }

    @Test
    @DisplayName("wiring: the launch does what the decision says, and the project's compiler waits out loud")
    void wiring() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java")).replace("\r\n", "\n");
        int method = src.indexOf("private static LanguageServerProvider.LanguageServerDescription nativeTypeScript(");
        assertThat(method).isPositive();
        String body = src.substring(method, src.indexOf("\n    }\n", method));
        assertThat(body).contains("TsNative.choose(found != null, v.workspace(), trusted, own != null)");
        assertThat(body).as("the compiler started is the one the decision named")
                .contains("File exe = choice == TsNative.Choice.FOUND ? found : own;");
        assertThat(body).as("trust is asked silently: the client calls this for every file").contains("ServerTrust.trusted.test(dir)");
        int waits = body.indexOf("case WAIT_FOR_TRUST -> {");
        assertThat(waits).isPositive();
        String waiting = body.substring(waits, body.indexOf("default ->", waits));
        assertThat(waiting).contains("ServerTrust.waits(found.getAbsolutePath(), dir);").contains("return null;")
                .as("nothing is started while it waits").doesNotContain("launch(");
        assertThat(body.substring(body.indexOf("default ->"))).contains("LanguageServerHealth.reportNoCompiler(v.version());");
    }
}

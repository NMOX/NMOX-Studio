package org.nmox.studio.rack.devices;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The script a folder with no project file would obviously run (3.8.0): one
 * file answers, a conventional name settles a tie, and anything that would be
 * a guess answers nothing.
 */
class ScriptEntryTest {

    private static Optional<ScriptEntry.Entry> choose(String... names) {
        return ScriptEntry.choose(List.of(names), n -> !n.endsWith("/"));
    }

    @Test
    @DisplayName("one script beside anything else is the one: its interpreter is the product's own spelling")
    void oneScriptIsObvious() {
        assertThat(choose("hello.py", "notes.md", "README")).contains(new ScriptEntry.Entry("hello.py", "python3 hello.py"));
        assertThat(choose("server.js", "data.json")).contains(new ScriptEntry.Entry("server.js", "node server.js"));
        assertThat(choose("tool.go")).contains(new ScriptEntry.Entry("tool.go", "go run tool.go"));
        assertThat(choose("x.rb")).contains(new ScriptEntry.Entry("x.rb", "ruby x.rb"));
        assertThat(choose("x.php")).contains(new ScriptEntry.Entry("x.php", "php x.php"));
        assertThat(choose("deploy.sh")).contains(new ScriptEntry.Entry("deploy.sh", "sh deploy.sh"));
        assertThat(choose("x.ts")).as("v2.69.0: one argv for every Node since 22.6")
                .contains(new ScriptEntry.Entry("x.ts", "node --experimental-strip-types x.ts"));
        assertThat(choose("Hello.PY")).as("the extension is read without case").map(ScriptEntry.Entry::command)
                .contains("python3 Hello.PY");
    }

    @Test
    @DisplayName("several scripts: exactly one conventional entry name wins; none or two is a guess, and a guess is refused")
    void conventionalNameSettlesATie() {
        assertThat(choose("main.py", "helpers.py", "util.py")).map(ScriptEntry.Entry::file).contains("main.py");
        assertThat(choose("lib.js", "app.js")).map(ScriptEntry.Entry::file).contains("app.js");
        assertThat(choose("Server.JS", "lib.js")).as("the stem is read without case").map(ScriptEntry.Entry::file)
                .contains("Server.JS");
        assertThat(choose("helpers.py", "util.py")).as("no conventional name among two").isEmpty();
        assertThat(choose("main.py", "app.js")).as("two conventional names: which language is a guess").isEmpty();
        assertThat(choose("main.py", "main.go")).isEmpty();
    }

    @Test
    @DisplayName("what is never a script: dotfiles, directories, files this product has no interpreter for, nothing at all")
    void whatIsNotAScript() {
        assertThat(choose()).isEmpty();
        assertThat(choose("README.md", "LICENSE", "notes.txt", "photo.jpg")).isEmpty();
        assertThat(choose(".hidden.py", "notes.md")).as("a dotfile is tooling, not the user's program").isEmpty();
        assertThat(ScriptEntry.choose(List.of("main.py/"), n -> false)).as("a directory named like a script").isEmpty();
        assertThat(choose("x.rs", "x.java", "x.c")).as("compiled languages need a build, which a manifest would say").isEmpty();
        assertThat(choose("py", "noext", "x.")).isEmpty();
    }

    @Test
    @DisplayName("a file name with whitespace is quoted the way SOLDER tokenizes; a quote, a control character or a leading dash is refused")
    void quotingFollowsSolder() {
        assertThat(choose("my script.py")).map(ScriptEntry.Entry::command).contains("python3 \"my script.py\"");
        assertThat(choose("it's.py")).as("no quoting makes an apostrophe inert in SOLDER's tokenizer").isEmpty();
        assertThat(choose("say\"hi\".py")).isEmpty();
        assertThat(choose("-x.py")).as("python3 -x.py is an option, not a file — a cloned repo picks its own names").isEmpty();
        assertThat(choose("--version.js")).isEmpty();
        assertThat(choose("line\nbreak.py")).as("a control character cannot ride an LCD line").isEmpty();
        assertThat(choose("a-b.py")).as("a dash inside a name is just a name").map(ScriptEntry.Entry::command).contains("python3 a-b.py");
    }

    @Test
    @DisplayName("the launch gate asks canRunIn: a manifest, or the one obvious script, lets a device run; a README alone does not")
    void theScriptIsTheManifestForTheLaunchGate(@TempDir Path tmp) throws Exception {
        Path scripts = Files.createDirectory(tmp.resolve("scripts"));
        Files.writeString(scripts.resolve("hello.py"), "");
        assertThat(ProjectInspector.hasProjectManifest(scripts.toFile()))
                .as("detection still says NONE — the project-root walks must not stop at a script folder").isFalse();
        assertThat(ProjectInspector.canRunIn(scripts.toFile())).as("but SOLDER may run here").isTrue();

        Path node = Files.createDirectory(tmp.resolve("node"));
        Files.writeString(node.resolve("package.json"), "{}");
        assertThat(ProjectInspector.canRunIn(node.toFile())).isTrue();

        Path readme = Files.createDirectory(tmp.resolve("readme"));
        Files.writeString(readme.resolve("README.md"), "# hi\n");
        assertThat(ProjectInspector.canRunIn(readme.toFile())).as("nothing to run: the NO PROJECT MANIFEST refusal stands").isFalse();
    }

    @Test
    @DisplayName("find() reads the folder's root by name only, skips directories, and refuses a listing past the cap")
    void findReadsTheRoot(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("hello.py"), "");
        Files.createDirectory(tmp.resolve("main.py"));
        Files.createDirectory(tmp.resolve("src"));
        Files.writeString(tmp.resolve("src").resolve("app.js"), "");
        assertThat(ScriptEntry.find(tmp.toFile())).as("the directory main.py is not a script; src/app.js is not at the root")
                .contains(new ScriptEntry.Entry("hello.py", "python3 hello.py"));
        assertThat(ScriptEntry.find(new File(tmp.toFile(), "missing"))).isEmpty();
        assertThat(ScriptEntry.find(null)).isEmpty();

        Path dump = Files.createDirectory(tmp.resolve("dump"));
        Files.writeString(dump.resolve("hello.py"), "");
        for (int i = 0; i <= ScriptEntry.MAX_ENTRIES; i++) {
            Files.writeString(dump.resolve("photo" + i + ".jpg"), "");
        }
        assertThat(ScriptEntry.find(dump.toFile())).as("a flat dump past the cap is not read further").isEmpty();
    }
}

package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * User-authored project templates (v1.293.0 — tech-debt item #1, the
 * extensibility arc). A JSON file in {@code ~/.nmox/templates.d}
 * becomes a wizard entry; these tests pin the three properties that
 * make that safe and dependable:
 *
 * <ul>
 *   <li>the parse discipline — {@code {{name}}} substitution, file
 *       order, skip-with-note for malformed drop-ins;</li>
 *   <li>the path law — a template writes ONLY strictly inside the
 *       target directory, and an unsafe path disqualifies the WHOLE
 *       template, because half a template is worse than none;</li>
 *   <li>the wizard wiring — customs join the same list and the same
 *       generate flow, but never the built-ins' pre-trust.</li>
 * </ul>
 */
class UserTemplatesTest {

    private static File dropIn(Path dir, String name, String json) throws IOException {
        File f = dir.resolve(name).toFile();
        Files.writeString(f.toPath(), json, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    @DisplayName("a valid drop-in parses: name, description, files in order")
    void parsesValidTemplate(@TempDir Path tmp) throws IOException {
        dropIn(tmp, "team.json", """
                { "name": "Team API", "description": "our starter",
                  "files": {
                    "package.json": "{ \\"name\\": \\"{{name}}\\" }",
                    "src/{{name}}.js": "// {{name}} entry"
                  } }
                """);
        UserTemplates.Loaded loaded = UserTemplates.load(tmp.toFile());

        assertThat(loaded.skipped()).isEmpty();
        assertThat(loaded.templates()).hasSize(1);
        UserTemplates.Custom t = loaded.templates().get(0);
        assertThat(t.name()).isEqualTo("Team API");
        assertThat(t.description()).isEqualTo("our starter");
        assertThat(t.files().keySet())
                .as("declaration order is generation order")
                .containsExactly("package.json", "src/{{name}}.js");
    }

    @Test
    @DisplayName("a malformed drop-in is skipped with a note; the good ones still load")
    void malformedFileIsSkippedNotFatal(@TempDir Path tmp) throws IOException {
        dropIn(tmp, "a-broken.json", "{ not json");
        dropIn(tmp, "b-nameless.json", "{ \"files\": { \"x\": \"y\" } }");
        dropIn(tmp, "c-good.json",
                "{ \"name\": \"Good\", \"files\": { \"x.txt\": \"hi\" } }");

        UserTemplates.Loaded loaded = UserTemplates.load(tmp.toFile());

        assertThat(loaded.templates()).extracting(UserTemplates.Custom::name)
                .as("one bad file must not hide the good ones — the learn-catalog law")
                .containsExactly("Good");
        assertThat(loaded.skipped()).extracting(UserTemplates.Skipped::file)
                .containsExactly("a-broken.json", "b-nameless.json");
    }

    @Test
    @DisplayName("an unsafe path disqualifies the WHOLE template")
    void unsafePathRefusesWholeTemplate(@TempDir Path tmp) throws IOException {
        dropIn(tmp, "evil.json", """
                { "name": "Evil", "files": {
                    "innocent.txt": "hello",
                    "../outside.txt": "escape"
                  } }
                """);
        UserTemplates.Loaded loaded = UserTemplates.load(tmp.toFile());

        assertThat(loaded.templates())
                .as("half a template is worse than none: the innocent file must"
                        + " not generate while the escape is quietly dropped")
                .isEmpty();
        assertThat(loaded.skipped()).hasSize(1);
        assertThat(loaded.skipped().get(0).reason()).contains("..");
    }

    @Test
    @DisplayName("the path law names every escape shape")
    void pathLaw() {
        assertThat(UserTemplates.pathProblem("src/app/main.js")).isNull();
        assertThat(UserTemplates.pathProblem("README.md")).isNull();
        assertThat(UserTemplates.pathProblem("/etc/passwd")).contains("absolute");
        assertThat(UserTemplates.pathProblem("~/x")).contains("absolute");
        assertThat(UserTemplates.pathProblem("a/../../b")).contains("..");
        assertThat(UserTemplates.pathProblem("..")).contains("..");
        assertThat(UserTemplates.pathProblem("a\\b")).contains("backslash");
        assertThat(UserTemplates.pathProblem("C:evil")).contains("drive");
        assertThat(UserTemplates.pathProblem("  ")).contains("blank");
        assertThat(UserTemplates.pathProblem("has..dots/in.name"))
                .as("only a whole .. SEGMENT is a traversal; dots in a name are fine")
                .isNull();
    }

    @Test
    @DisplayName("generate writes the declared files with {{name}} substituted, nothing else")
    void generateWritesExactlyTheTemplate(@TempDir Path tmp) throws IOException {
        UserTemplates.Custom t = UserTemplates.parse("""
                { "name": "T", "files": {
                    "package.json": "{ \\"name\\": \\"{{name}}\\" }",
                    "src/{{name}}.md": "# {{name}}"
                  } }
                """, new File("t.json"));
        File dir = tmp.resolve("proj").toFile();

        UserTemplates.generate(t, dir, "invoicer");

        assertThat(new File(dir, "package.json"))
                .content().contains("\"name\": \"invoicer\"");
        assertThat(new File(dir, "src/invoicer.md"))
                .as("substitution applies to paths too").content().isEqualTo("# invoicer");
        assertThat(dir.listFiles())
                .as("no extra files: the template IS the contract")
                .extracting(File::getName).containsExactlyInAnyOrder("package.json", "src");
    }

    @Test
    @DisplayName("generate refuses a non-empty target, exactly like the built-ins")
    void generateRefusesNonEmptyTarget(@TempDir Path tmp) throws IOException {
        UserTemplates.Custom t = UserTemplates.parse(
                "{ \"name\": \"T\", \"files\": { \"a\": \"b\" } }", new File("t.json"));
        File dir = tmp.resolve("proj").toFile();
        assertThat(dir.mkdirs()).isTrue();
        Files.writeString(dir.toPath().resolve("existing.txt"), "x");

        assertThatThrownBy(() -> UserTemplates.generate(t, dir, "p"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not empty");
    }

    @Test
    @DisplayName("the wizard lists customs but never grants them the built-ins' pre-trust")
    void wizardWiring() throws IOException {
        // CRLF checkouts (the windows lane) would break the multi-line
        // literal below — normalize before asserting (the v1.42.0 lesson)
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "rack", "projectstudio", "NewProjectDialog.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(src)
                .as("customs must join the wizard's list, or the drop-in dir is dead")
                .contains("UserTemplates.load(UserTemplates.dropInDir())");
        assertThat(src)
                .as("a custom template's content is drop-in data that may have"
                        + " been copied from anywhere — pre-trusting it would"
                        + " silence the exact prompt WorkspaceTrust exists for")
                .contains("if (!custom) {\n                WorkspaceTrust.trust(dir);");
        assertThat(src)
                .as("the optional install spawn on a custom template must ask"
                        + " first (the v1.224.0 spawn-ledger law)")
                .contains("(!custom || WorkspaceTrust.requestTrust(dir))");
    }
}

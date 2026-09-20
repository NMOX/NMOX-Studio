package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.Containment;

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

    /**
     * A platform that refuses symbolic links has nothing to prove in the
     * link tests — but it must SAY so. A bare {@code return} here would
     * make the whole test body vanish under a green tick (the v2.186.0
     * {@code argvPinned} defect); an assumption skips it in the open.
     */
    private static void assumeLinked(Path from, Path to) {
        try {
            Files.createSymbolicLink(from, to);
        } catch (UnsupportedOperationException | IOException noSymlinks) {
            Assumptions.abort("this platform will not create symbolic links: " + noSymlinks);
        }
    }

    private static UserTemplates.Custom oneFile(String key, String content) throws IOException {
        return UserTemplates.parse("{ \"name\": \"T\", \"files\": { \""
                + key + "\": \"" + content + "\" } }", new File("t.json"));
    }

    // ---- ledger 117: generate() places every write with core.util.Containment ----

    @Test
    @DisplayName("a location carrying a .. segment writes the template instead of refusing every file")
    void aDottedLocationNoLongerRefusesEveryFile(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("old"));
        Files.createDirectories(tmp.resolve("projects"));
        // exactly what the wizard builds — new File(locationField, name) —
        // and that location field is free text a user can type or paste
        File dir = new File(tmp.resolve("old/../projects").toString(), "proj");

        UserTemplates.generate(oneFile("package.json", "{}"), dir, "p");

        assertThat(tmp.resolve("projects").resolve("proj").resolve("package.json"))
                .as("the lexical guard normalized the TARGET and left the BASE as"
                        + " typed, so the two could never share a prefix and every"
                        + " file of an ordinary template was refused as an escape")
                .exists();
    }

    @Test
    @DisplayName("a path resolving to the project root is refused in the template's own words")
    void aPathResolvingToTheRootIsRefusedInOurOwnWords(@TempDir Path tmp) throws IOException {
        UserTemplates.Custom t = UserTemplates.parse(
                "{ \"name\": \"T\", \"files\": { \"{{name}}\": \"x\" } }", new File("t.json"));
        File dir = tmp.resolve("proj").toFile();

        assertThatThrownBy(() -> UserTemplates.generate(t, dir, "."))
                .as("this used to reach writeString and come back as the operating"
                        + " system's raw \"Is a directory\" — a refusal that names no"
                        + " template and belongs to nobody")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Refusing to write outside the project");
    }

    @Test
    @DisplayName("a symlinked segment under the project cannot be written through")
    void aSymlinkedSegmentIsRefusedAndNothingLandsOutside(@TempDir Path tmp) throws IOException {
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Path root = Files.createDirectories(tmp.resolve("proj"));
        assumeLinked(root.resolve("pub"), outside);
        UserTemplates.Custom t = oneFile("pub/pwned.txt", "owned");

        // The guard's own verdict, asserted rather than assumed: the
        // never-clobber check below refuses first, so a guard that had
        // quietly stopped refusing would hide behind it forever. The
        // lexical spelling this replaced answered startsWith(root)=TRUE
        // for exactly this path — measured before the change.
        assertThat(Containment.resolvePath(root.toFile(), "pub/pwned.txt"))
                .as("a link leaving the project is not a place inside it")
                .isNull();

        assertThatThrownBy(() -> UserTemplates.generate(t, root.toFile(), "p"))
                .as("and the never-clobber law gets there first — which is why this"
                        + " was never reachable, not why the guard may be weaker")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not empty");
        assertThat(outside.resolve("pwned.txt")).doesNotExist();
    }

    @Test
    @DisplayName("a symlinked LOCATION is not an escape — that is the directory the user chose")
    void aSymlinkedLocationStillWrites(@TempDir Path tmp) throws IOException {
        Path real = Files.createDirectories(tmp.resolve("real"));
        assumeLinked(tmp.resolve("link"), real);
        File dir = new File(tmp.resolve("link").toFile(), "proj");

        UserTemplates.generate(oneFile("package.json", "{}"), dir, "p");

        assertThat(real.resolve("proj").resolve("package.json"))
                .as("canonicalizing the TARGET without canonicalizing the ROOT would"
                        + " refuse every project under a symlinked home or volume")
                .exists();
    }

    @Test
    @DisplayName("a traversal that only appears after {{name}} substitution is still refused")
    void aSubstitutedTraversalIsStillRefused(@TempDir Path tmp) throws IOException {
        // pathProblem() judges the DECLARED key, which is clean here — this
        // is the hole the generate-time guard exists to keep shut
        UserTemplates.Custom t = UserTemplates.parse(
                "{ \"name\": \"T\", \"files\": { \"{{name}}/x.txt\": \"escape\" } }",
                new File("t.json"));
        File dir = tmp.resolve("proj").toFile();

        assertThatThrownBy(() -> UserTemplates.generate(t, dir, "../evil"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Refusing to write outside the project");
        assertThat(tmp.resolve("evil")).doesNotExist();
    }

    @Test
    @DisplayName("an absolute path is JOINED inside the project, never followed out")
    void anAbsolutePathIsJoinedNotFollowed(@TempDir Path tmp) throws IOException {
        // The one place the guard is more permissive than the spelling it
        // replaced, named so it is a decision rather than a drift: File's
        // own join rule puts /etc/x at <project>/etc/x — contained, which
        // is all containment promises — where the lexical Path.resolve let
        // the absolute win and then refused it. Unreachable from the
        // wizard either way: a DECLARED absolute is refused at parse time
        // by pathProblem(), and sanitizedName() turns every '/' in a
        // project name into '-' before it can reach a substitution.
        UserTemplates.Custom t = UserTemplates.parse(
                "{ \"name\": \"T\", \"files\": { \"{{name}}/x.txt\": \"joined\" } }",
                new File("t.json"));
        File dir = tmp.resolve("proj").toFile();

        UserTemplates.generate(t, dir, "/etc");

        assertThat(dir.toPath().resolve("etc").resolve("x.txt")).exists();
        assertThat(Path.of("/etc/x.txt")).doesNotExist();
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

package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The community drop-in dir ({@code ~/.nmox/learn-catalog.d} in
 * production, a temp dir here — tests never touch the real home), held
 * to its laws: same schema as the built-in catalog, override-by-slug in
 * place, built-ins-then-filename-order determinism, malformed files
 * skipped with the file named (never a crash, never a blocked picker),
 * and an empty or missing dir behaving exactly like the pre-drop-in
 * product.
 */
class LearningCatalogDropInTest {

    /** A complete, schema-faithful drop-in space — the worked example
     * from docs/learning-spaces.md, kept in sync by the round-trip test. */
    private static String zigSpaceJson() {
        return """
            {
              "spaces": [
                {
                  "slug": "zig",
                  "name": "Zig",
                  "category": "LANGUAGE",
                  "family": "Systems",
                  "blurb": "Manual memory without the footguns.",
                  "driver": {
                    "kind": "run",
                    "command": ["zig", "run", "hello.zig"],
                    "prompt": "",
                    "snippets": []
                  },
                  "install": {
                    "mac": "brew install zig",
                    "linux": "sudo apt install zig",
                    "windows": "choco install zig"
                  },
                  "files": [
                    { "path": "hello.zig",
                      "content": "const std = @import(\\"std\\");\\npub fn main() !void {\\n    std.debug.print(\\"Hello, Zig!\\\\n\\", .{});\\n}\\n" }
                  ],
                  "tutorial": "# Zig\\n\\nPress GO to run hello.zig."
                }
              ]
            }
            """;
    }

    private static void write(File dir, String name, String content) throws Exception {
        Files.writeString(new File(dir, name).toPath(), content, StandardCharsets.UTF_8);
    }

    private static List<String> slugs(List<LearningCatalog.Space> spaces) {
        List<String> out = new ArrayList<>();
        spaces.forEach(s -> out.add(s.slug()));
        return out;
    }

    @Test
    @DisplayName("A drop-in file round-trips the full built-in schema: every field lands")
    void dropInSchemaRoundTrip(@TempDir File dir) throws Exception {
        write(dir, "zig.json", zigSpaceJson());

        List<LearningCatalog.Space> all = LearningCatalog.allFrom(dir);
        LearningCatalog.Space zig = all.stream()
                .filter(s -> s.slug().equals("zig")).findFirst().orElseThrow();

        assertThat(zig.name()).isEqualTo("Zig");
        assertThat(zig.category()).isEqualTo(LearningCatalog.Category.LANGUAGE);
        assertThat(zig.family()).isEqualTo("Systems");
        assertThat(zig.blurb()).isEqualTo("Manual memory without the footguns.");
        assertThat(zig.driver().kind()).isEqualTo(LearningCatalog.DriverKind.RUN);
        assertThat(zig.driver().command()).containsExactly("zig", "run", "hello.zig");
        assertThat(zig.install()).containsKeys("mac", "linux", "windows");
        assertThat(zig.files()).hasSize(1);
        assertThat(zig.files().get(0).path()).isEqualTo("hello.zig");
        assertThat(zig.files().get(0).content()).contains("Hello, Zig!");
        assertThat(zig.tutorial()).startsWith("# Zig");
        // and the generation path accepts it: the availability probe and
        // install hint read the same fields the picker does
        assertThat(LearningSpace.installHint(zig)).isNotBlank();
    }

    @Test
    @DisplayName("A drop-in slug matching a built-in overrides it IN PLACE — same slot, new content")
    void overrideByIdReplacesInPlace(@TempDir File dir) throws Exception {
        List<LearningCatalog.Space> builtIns = LearningCatalog.builtIns();
        int pythonAt = slugs(builtIns).indexOf("python");
        assertThat(pythonAt).as("python is a built-in").isNotNegative();

        write(dir, "better-python.json", """
            {"spaces":[{"slug":"python","name":"Python (community)",
              "category":"LANGUAGE","family":"Python","blurb":"improved",
              "driver":{"kind":"repl","command":["python3","-i","-q"],
                        "prompt":">>>","snippets":["1+1"]}}]}
            """);

        List<LearningCatalog.Space> all = LearningCatalog.allFrom(dir);
        assertThat(all).as("an override adds no entry").hasSameSizeAs(builtIns);
        assertThat(all.get(pythonAt).slug()).as("same slot").isEqualTo("python");
        assertThat(all.get(pythonAt).name()).as("new content").isEqualTo("Python (community)");
        assertThat(slugs(all)).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("A malformed file is skipped and NAMED — its neighbours still load, nothing throws")
    void malformedFileSkippedWithWarning(@TempDir File dir) throws Exception {
        write(dir, "aa-broken.json", "{ this is not json");
        write(dir, "bb-missing-fields.json", "{\"spaces\":[{\"name\":\"no slug\"}]}");
        write(dir, "cc-good.json", zigSpaceJson());

        List<File> reported = new ArrayList<>();
        List<LearningCatalog.Space> all = LearningCatalog.allFrom(dir,
                (file, ex) -> reported.add(file));

        assertThat(slugs(all)).as("the good neighbour loads").contains("zig");
        assertThat(reported).extracting(File::getName)
                .as("each bad file is reported by name, once")
                .containsExactly("aa-broken.json", "bb-missing-fields.json");
        assertThat(all.size()).as("built-ins are all still there")
                .isEqualTo(LearningCatalog.builtIns().size() + 1);
    }

    @Test
    @DisplayName("Order is deterministic: built-ins first, then drop-ins by FILENAME, not creation order")
    void orderingIsBuiltInsThenFilename(@TempDir File dir) throws Exception {
        // written b-then-a on purpose: filename must win over creation order
        write(dir, "bb.json", zigSpaceJson().replace("\"zig\"", "\"zz-from-bb\""));
        write(dir, "aa.json", zigSpaceJson().replace("\"zig\"", "\"zz-from-aa\""));

        List<String> all = slugs(LearningCatalog.allFrom(dir));
        int builtInCount = LearningCatalog.builtIns().size();

        assertThat(all.subList(0, builtInCount))
                .as("built-ins keep their catalog order")
                .isEqualTo(slugs(LearningCatalog.builtIns()));
        assertThat(all.subList(builtInCount, all.size()))
                .as("drop-ins append sorted by filename")
                .containsExactly("zz-from-aa", "zz-from-bb");
    }

    @Test
    @DisplayName("Empty or missing dir = exactly today's behavior: the built-in cache itself")
    void emptyOrMissingDirIsExactlyBuiltIns(@TempDir File empty) {
        assertThat(LearningCatalog.allFrom(empty))
                .as("empty dir hands back the built-in list instance")
                .isSameAs(LearningCatalog.builtIns());
        assertThat(LearningCatalog.allFrom(new File(empty, "does-not-exist")))
                .as("missing dir likewise")
                .isSameAs(LearningCatalog.builtIns());
    }

    @Test
    @DisplayName("An unchanged dir is served from cache — no re-parse, no re-warning storm")
    void unchangedDirDoesNotReparseOrRewarn(@TempDir File dir) throws Exception {
        write(dir, "bad.json", "not json at all");
        write(dir, "good.json", zigSpaceJson());

        AtomicInteger warnings = new AtomicInteger();
        List<LearningCatalog.Space> first = LearningCatalog.allFrom(dir,
                (f, ex) -> warnings.incrementAndGet());
        List<LearningCatalog.Space> second = LearningCatalog.allFrom(dir,
                (f, ex) -> warnings.incrementAndGet());

        assertThat(second).as("cache hit returns the same list").isSameAs(first);
        assertThat(warnings.get())
                .as("the user is told about the bad file once, not per picker open").isEqualTo(1);
    }

    @Test
    @DisplayName("Editing a drop-in file is picked up on the next load — no IDE restart")
    void editedFileInvalidatesTheCache(@TempDir File dir) throws Exception {
        File f = new File(dir, "zig.json");
        write(dir, "zig.json", zigSpaceJson());
        List<LearningCatalog.Space> before = LearningCatalog.allFrom(dir);
        assertThat(before).extracting(LearningCatalog.Space::name).contains("Zig");

        write(dir, "zig.json", zigSpaceJson().replace("\"Zig\"", "\"Zig 2\""));
        // mtime granularity can be a full second on some filesystems;
        // force a distinct stamp the way a real later edit would have one
        assertThat(f.setLastModified(f.lastModified() + 2000)).isTrue();

        assertThat(LearningCatalog.allFrom(dir))
                .extracting(LearningCatalog.Space::name)
                .as("the edit is live on the next picker open").contains("Zig 2");
    }

    @Test
    @DisplayName("The merged list is as immutable as the built-in one")
    void mergedListIsImmutable(@TempDir File dir) throws Exception {
        write(dir, "zig.json", zigSpaceJson());
        List<LearningCatalog.Space> all = LearningCatalog.allFrom(dir);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> all.add(all.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("merge(): override replaces in place, new slugs append, later drop-in wins")
    void mergeLawDirect() {
        LearningCatalog.Space a = space("a", "A");
        LearningCatalog.Space b = space("b", "B");
        LearningCatalog.Space b2 = space("b", "B improved");
        LearningCatalog.Space c = space("c", "C");
        LearningCatalog.Space c2 = space("c", "C later");

        List<LearningCatalog.Space> merged = LearningCatalog.merge(
                List.of(a, b), List.of(b2, c, c2));

        assertThat(slugs(merged)).containsExactly("a", "b", "c");
        assertThat(merged.get(1).name()).as("override in place").isEqualTo("B improved");
        assertThat(merged.get(2).name()).as("duplicate drop-in slug: later wins")
                .isEqualTo("C later");
    }

    private static LearningCatalog.Space space(String slug, String name) {
        return new LearningCatalog.Space(slug, name, LearningCatalog.Category.LANGUAGE,
                "fam", "blurb",
                new LearningCatalog.Driver(LearningCatalog.DriverKind.REPL,
                        List.of("x"), ">", List.of()),
                java.util.Map.of(), List.of(), "t");
    }
}

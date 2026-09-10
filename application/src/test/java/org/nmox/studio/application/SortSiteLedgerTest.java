package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every string sort in the product says who is reading it (ledger 92,
 * v2.104.0) — the {@code ClockSiteLedgerTest} idiom, applied to order.
 *
 * <p>Sorting by code point is not alphabetical order in any language with
 * letters past ASCII. Measured before this ledger was written:
 * {@code Ćwiczenie} lands after {@code Zamknij} in Polish, {@code Ändern}
 * after {@code Zoom} in German, {@code Ідея} after {@code Явище} in
 * Ukrainian, {@code Ăn} after {@code Xem} in Vietnamese. Every accented
 * word is exiled to the end, which is where a reader will not look.
 *
 * <p>And the opposite is just as real: a drop-in reading order, a wire
 * order tests pin, a version directory chosen by picking the last one, and
 * a Markdown tree pasted into a README must all be identical on two
 * machines, so those stay on code points ON PURPOSE. A version sort under
 * a collator would be an actual bug, not a cosmetic one.
 *
 * <p>The rule: a file that sorts strings either routes through
 * {@code Collate} or is named here as STABLE with its reason. A new one
 * fails the build until someone decides.
 */
class SortSiteLedgerTest {

    private static final Pattern SORT =
            Pattern.compile("\\.sort\\(|Arrays\\.sort\\(|Collections\\.sort\\(");

    /** Comparators that never compare a string — not this ledger's business. */
    private static final Pattern NOT_A_STRING =
            Pattern.compile("comparingInt|comparingLong|comparingDouble|reverseOrder");

    private static final Pattern STRINGY = Pattern.compile(
            "compareTo|compareToIgnoreCase|comparing\\(|String::|toLowerCase|getName\\(\\)");

    private static final List<String> MODULES = List.of("core", "editor", "tools", "project",
            "ui", "rack", "apiclient", "dbstudio", "web3", "infra");

    private static final String DROP_IN =
            "the drop-in law: files are read in filename order so a user can "
            + "predict which of their own files wins, on any machine";

    /**
     * STABLE by decision: an order two machines must agree on. Each value
     * says why the reader's language must not touch it.
     */
    private static final Map<String, String> STABLE = Map.ofEntries(
            Map.entry("ToolLocator.java",
                    "version directory names, sorted to pick the newest — a collator "
                    + "would reorder version numbers and select the wrong toolchain"),
            Map.entry("TreeText.java",
                    "the project tree pasted into a README: a record, and it must come "
                    + "out identical on the author's machine and the reader's"),
            Map.entry("IrcClient.java",
                    "the wire order the protocol tests pin, byte for byte"),
            Map.entry("McpCompletions.java",
                    "an agent reads these, and two agents comparing transcripts need "
                    + "the same order regardless of whose desk they ran on"),
            Map.entry("JavaScriptCompletionProvider.java",
                    "JavaScript's own keywords and globals — the language's fixed ASCII "
                    + "vocabulary, not words in the reader's language"),
            Map.entry("ClassicLibraryDetector.java",
                    "file paths from a disk scan, bounded and deterministic"),
            Map.entry("NpmService.java",
                    "npm package names, which npm itself restricts to lowercase ASCII, "
                    + "so a collator would order them identically at more cost"),
            Map.entry("UserDevices.java", DROP_IN),
            Map.entry("UserPresets.java", DROP_IN),
            Map.entry("UserProbes.java", DROP_IN),
            Map.entry("UserTemplates.java", DROP_IN),
            Map.entry("DockerRecipes.java", DROP_IN),
            Map.entry("HttpLibrary.java", DROP_IN),
            Map.entry("LearningCatalog.java", DROP_IN),
            Map.entry("LegacyWeb.java",
                    "pages found by a disk scan, ordered so a re-scan finds the same one"),
            Map.entry("ProjectInspector.java",
                    "a directory scan whose result feeds detection, not a list on screen"),
            Map.entry("PwaKit.java",
                    "the icon files the kit writes, named and ordered by the generator"),
            Map.entry("Workspaces.java",
                    "monorepo package directories, read by the lanes rather than by a person"));

    @Test
    @DisplayName("every string sort is either Collate or a named STABLE site")
    void everySortIsClassified() throws IOException {
        List<String> unclassified = new ArrayList<>();
        int found = 0;
        for (Path p : sources()) {
            // CRLF on the Windows lane would put a \r on every window boundary
            String body = Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
            String[] lines = body.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (!SORT.matcher(lines[i]).find()) {
                    continue;
                }
                String window = String.join("\n",
                        java.util.Arrays.asList(lines).subList(i, Math.min(i + 3, lines.length)));
                if (NOT_A_STRING.matcher(window).find() || !STRINGY.matcher(window).find()) {
                    continue;
                }
                found++;
                String file = p.getFileName().toString();
                if (!body.contains("Collate.") && !STABLE.containsKey(file)) {
                    unclassified.add(file + ":" + (i + 1) + " — a sort nobody has said who reads");
                }
            }
        }
        assertThat(found).as("the census should find the product's string sorts").isGreaterThan(15);
        assertThat(unclassified)
                .as("classify it: Collate.byDisplayName for a person, Collate.stableBy for a record")
                .isEmpty();
    }

    @Test
    @DisplayName("every STABLE reason is written down, and the list holds no ghosts")
    void reasonsAreRealAndCurrent() throws IOException {
        List<String> files = sources().stream().map(p -> p.getFileName().toString()).toList();
        List<String> ghosts = new ArrayList<>();
        List<String> reasonless = new ArrayList<>();
        for (Map.Entry<String, String> e : STABLE.entrySet()) {
            if (!files.contains(e.getKey())) {
                ghosts.add(e.getKey());
            }
            if (e.getValue().length() < 30) {
                reasonless.add(e.getKey());
            }
        }
        assertThat(ghosts).as("named STABLE but no longer in the product — a stale blessing").isEmpty();
        assertThat(reasonless).as("a blessing without a reason is a guess").isEmpty();
    }

    private static List<Path> sources() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                out.addAll(files.filter(f -> f.toString().endsWith(".java")).toList());
            }
        }
        return out;
    }
}

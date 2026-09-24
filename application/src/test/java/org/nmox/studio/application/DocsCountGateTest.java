package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The counts the docs quote must be the counts the product has.
 *
 * <p>Every release that adds a device or a learning space silently
 * falsifies a sentence somewhere in the docs, and nothing reads those
 * sentences — which is why the v1.361.0 docs pass found a tutorial
 * still promising 51 devices (two releases of devices ago) and another
 * promising 78 learning spaces (twelve spaces ago). A number in prose
 * is a claim with no test behind it; this is that test.
 *
 * <p>Scope is deliberately the LIVE documents — the ones a user is
 * told to read. The dated records (CHANGELOG.md and the plan's
 * addenda) quote the counts that were true on the day they were
 * written and must NOT be dragged forward: a record that silently
 * updates itself is not a record. Since 3.0.1 there is no third
 * category — the v0.x archaeology under docs/hack and docs/product
 * was deleted rather than banner-labelled, so every other document
 * in docs/ is live by construction.
 */
class DocsCountGateTest {

    /**
     * The live documents: what README and the user guide and the
     * tutorials tell a reader today. Anything not in this list is
     * either generated (devices.md) or a dated record.
     */
    private static List<Path> liveDocs() throws IOException {
        List<Path> docs = new ArrayList<>();
        docs.add(Path.of("..", "README.md"));
        docs.add(Path.of("..", "docs", "user-guide.md"));
        // the Kitchen Sink quotes counts too — its "85 grammars" rotted
        // for two releases because this census missed it (v2.34.2)
        docs.add(Path.of("..", "docs", "kitchen-sink.md"));
        docs.add(Path.of("..", "docs", "engineering", "codebase-guide.md"));
        // CONTRIBUTING.md opens by counting the product for a new
        // contributor, and v3.1.0 found two of its three counts stale
        // (86 grammars against 88, 92 spaces against 93) with no census
        // reading it
        docs.add(Path.of("..", "CONTRIBUTING.md"));
        // CLAUDE.md's reference body quotes counts too, and a 2026-09-18
        // review found six of them stale by up to forty releases (72
        // grammars against 88, 16 manifests against 60) — the file most
        // often read first was the one file no census read. Only its
        // undated body is live: see liveLines.
        docs.add(CLAUDE_MD);
        try (Stream<Path> tutorials = Files.list(Path.of("..", "docs", "tutorials"))) {
            tutorials.filter(p -> p.getFileName().toString().endsWith(".md")).forEach(docs::add);
        }
        return docs;
    }

    private static final Path CLAUDE_MD = Path.of("..", "CLAUDE.md");

    /**
     * A document's lines with the dated parts blanked, numbering kept.
     * CLAUDE.md opens with a status paragraph and closes with a version
     * history that both quote the counts of their day on purpose ("v1.92.0:
     * 78 total"); what it tells a reader TODAY sits between the Module
     * Structure heading and Known Issues. Every other live doc is live
     * throughout.
     */
    private static List<String> liveLines(Path doc) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(doc));
        if (!doc.equals(CLAUDE_MD)) {
            return lines;
        }
        boolean live = false;
        boolean sawBody = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("## Module Structure")) {
                live = true;
                sawBody = true;
            } else if (line.startsWith("## Known Issues")) {
                live = false;
            }
            if (!live) {
                lines.set(i, "");
            }
        }
        assertThat(sawBody).as("CLAUDE.md lost its '## Module Structure' heading — the census reads nothing").isTrue();
        return lines;
    }

    /**
     * Every number a live doc quotes for one kind of thing, with the
     * file and line it came from so a failure names the sentence to
     * fix rather than just the number.
     */
    private static List<String> claims(Pattern claim, List<Integer> found) throws IOException {
        List<String> where = new ArrayList<>();
        for (Path doc : liveDocs()) {
            List<String> lines = liveLines(doc);
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = claim.matcher(lines.get(i));
                while (m.find()) {
                    found.add(Integer.parseInt(m.group(1)));
                    where.add(doc.getFileName() + ":" + (i + 1) + " — " + m.group());
                }
            }
        }
        return where;
    }

    @Test
    @DisplayName("every live doc that counts devices agrees with the generated device reference")
    void deviceCount() throws Exception {
        // docs/devices.md is generated from DeviceCatalog by DeviceDocsTest
        // and CI fails on drift, so its section count IS the device count.
        long devices = Files.readAllLines(Path.of("..", "docs", "devices.md")).stream()
                .filter(l -> l.startsWith("### "))
                .count();
        assertThat(devices).as("the generated reference should list devices").isGreaterThan(40);

        List<Integer> found = new ArrayList<>();
        List<String> where = claims(Pattern.compile("(\\d+) devices"), found);
        assertThat(found).as("no live doc counts devices at all — did the phrasing change?").isNotEmpty();
        assertThat(found)
                .as("stale device counts (truth is %d): %s", devices, where)
                .allMatch(n -> n == devices);
    }

    @Test
    @DisplayName("every live doc that counts learning spaces agrees with the catalog")
    void learningSpaceCount() throws Exception {
        // One "slug" per space: a space without a slug is not a space
        // (the catalog is keyed by it), so slugs count spaces exactly.
        String catalog = Files.readString(Path.of("..", "rack", "src", "main", "resources",
                "org", "nmox", "studio", "rack", "projectstudio", "learn-catalog.json"));
        long spaces = Pattern.compile("\"slug\"\\s*:").matcher(catalog).results().count();
        assertThat(spaces).as("the catalog should hold spaces").isGreaterThan(50);

        List<Integer> found = new ArrayList<>();
        // CASE_INSENSITIVE is load-bearing: README's badge strip shouts
        // "91 LEARNING SPACES" in caps, and the case-sensitive form let
        // it sit three releases stale while this gate stayed green
        // (found in the v2.18.0 polish pass)
        List<String> where = claims(Pattern.compile("(\\d+) (?:learning spaces|built in|spaces\\b)",
                Pattern.CASE_INSENSITIVE), found);
        assertThat(found).as("no live doc counts learning spaces at all — did the phrasing change?").isNotEmpty();
        assertThat(found)
                .as("stale learning-space counts (truth is %d): %s", spaces, where)
                .allMatch(n -> n == spaces);
    }

    @Test
    @DisplayName("every live doc that counts manifests agrees with WebProjectFactory")
    void manifestCount() throws Exception {
        // The manifests are the door: a name among them opens that checkout
        // as a platform project. This used to COUNT THE LITERALS between
        // `MANIFESTS = {` and its closing brace — a population that was a
        // spelling rather than an outcome, and v2.184.0 broke it by making
        // the list DERIVED from ProjectKind: the literals dropped to the
        // three the factory adds itself, and the gate read 2 where the real
        // answer is past sixty. Ask the factory instead of reading how it is
        // written; the same class this project has now paid for in
        // MultiMimeSingletonGateTest (v2.19.1), the popup census (v2.146.0)
        // and the bounded-read ledger (v2.181.0).
        long manifests = countManifests();
        assertThat(manifests).as("the factory should recognize manifests").isGreaterThan(40);

        List<Integer> found = new ArrayList<>();
        // three phrasings live in the docs — a gate that knows only one
        // of them lets the other two rot (the v2.18.0 polish pass found
        // "58 recognized manifests" and "58 manifest types" two counts
        // stale under a green gate)
        List<String> where = claims(Pattern.compile(
                "(\\d+) (?:manifest names|recognized manifests|manifest types)",
                Pattern.CASE_INSENSITIVE), found);
        assertThat(found)
                .as("stale manifest counts (truth is %d): %s", manifests, where)
                .allMatch(n -> n == manifests);
    }

    @Test
    @DisplayName("every live doc that counts grammars agrees with the vendored set")
    void grammarCount() throws Exception {
        // one .tmLanguage.json per vendored grammar — the same set the
        // README calls the polyglot editor's engine. Ungated until the
        // v2.18.0 polish pass, which found the README's badge AND pitch
        // line seven grammars behind the README's own body text.
        long grammars;
        try (Stream<Path> files = Files.walk(Path.of("..", "editor", "src",
                "main", "resources"))) {
            grammars = files.filter(p -> p.getFileName().toString()
                    .endsWith(".tmLanguage.json")).count();
        }
        assertThat(grammars).as("the editor should vendor grammars").isGreaterThan(70);

        List<Integer> found = new ArrayList<>();
        List<String> where = claims(Pattern.compile(
                "(\\d+)(?:-grammar| (?:language |textmate )?grammars\\b)",
                Pattern.CASE_INSENSITIVE), found);
        assertThat(found).as("no live doc counts grammars at all — did the phrasing change?").isNotEmpty();
        assertThat(found)
                .as("stale grammar counts (truth is %d): %s", grammars, where)
                .allMatch(n -> n == grammars);
    }

    /**
     * Spelled numbers, because the docs write "thirteen languages" and not
     * "13 languages" — which is exactly why the digit-matching census above
     * could never see this count. The gate's third blind spot (v2.18.0 lost
     * three, v2.34.2 lost two more): a claim the census cannot parse is a
     * claim that rots silently.
     */
    private static final Map<String, Integer> SPELLED = Map.ofEntries(
            Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3),
            Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6),
            Map.entry("seven", 7), Map.entry("eight", 8), Map.entry("nine", 9),
            Map.entry("ten", 10), Map.entry("eleven", 11), Map.entry("twelve", 12),
            Map.entry("thirteen", 13), Map.entry("fourteen", 14), Map.entry("fifteen", 15),
            Map.entry("sixteen", 16), Map.entry("seventeen", 17), Map.entry("eighteen", 18),
            Map.entry("nineteen", 19), Map.entry("twenty", 20));

    @Test
    @DisplayName("every live doc that counts languages agrees with UiLocale.SUPPORTED")
    void languageCountsAreCurrent() throws IOException {
        // ground truth: the choices the Options combo really offers, less the
        // "System default" row, which is not a language
        String uiLocale = Files.readString(Path.of("..", "core", "src", "main", "java", "org",
                "nmox", "studio", "core", "util", "UiLocale.java"));
        long languages = Pattern.compile("new Choice\\(\"[a-z]{2}\"").matcher(uiLocale).results().count();
        assertThat(languages).as("UiLocale should offer languages").isGreaterThan(5);

        Pattern claim = Pattern.compile("(?i)\\b([a-z]+) languages\\b");
        List<String> stale = new ArrayList<>();
        int seen = 0;
        for (Path doc : liveDocs()) {
            List<String> lines = liveLines(doc);
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = claim.matcher(lines.get(i));
                while (m.find()) {
                    Integer n = SPELLED.get(m.group(1).toLowerCase(java.util.Locale.ROOT));
                    if (n == null) {
                        continue;   // "many languages", "these languages" — not a count
                    }
                    seen++;
                    if (n != languages) {
                        stale.add(doc.getFileName() + ":" + (i + 1) + " — " + m.group());
                    }
                }
            }
        }
        assertThat(seen).as("no live doc counts languages at all — did the phrasing change?").isPositive();
        assertThat(stale).as("stale language counts (truth is %d)", languages).isEmpty();
    }

    /**
     * How many manifest names the factory opens a project on, read out of the
     * built classes rather than out of the source text.
     *
     * <p>Reflection because {@code application} has no compile dependency on
     * {@code tools}: the gate runs after the cluster is assembled, so the jar
     * is on disk either way, and asking the code is the only reading that
     * cannot be invalidated by rewriting how the list is produced.
     */
    private static long countManifests() throws Exception {
        Path classes = Path.of("..", "tools", "target", "classes");
        assertThat(Files.isDirectory(classes))
                .as("tools must be compiled for this gate: %s", classes.toAbsolutePath())
                .isTrue();
        try (java.net.URLClassLoader loader = new java.net.URLClassLoader(
                new java.net.URL[]{classes.toUri().toURL(), Path.of("..", "rack", "target", "classes")
                    .toUri().toURL()}, DocsCountGateTest.class.getClassLoader())) {
            Class<?> factory = loader.loadClass("org.nmox.studio.tools.npm.WebProjectFactory");
            java.lang.reflect.Method walked = factory.getDeclaredMethod("walkedManifests");
            walked.setAccessible(true);
            return ((List<?>) walked.invoke(null)).size();
        }
    }

}

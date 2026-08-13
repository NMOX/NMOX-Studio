package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * told to read. The historical logs (CHANGELOG.md, the plan's dated
 * addenda, docs/hack, docs/product) quote the counts that were true
 * on the day they were written and must NOT be dragged forward: a
 * record that silently updates itself is not a record.
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
        docs.add(Path.of("..", "docs", "engineering", "codebase-guide.md"));
        try (Stream<Path> tutorials = Files.list(Path.of("..", "docs", "tutorials"))) {
            tutorials.filter(p -> p.getFileName().toString().endsWith(".md")).forEach(docs::add);
        }
        return docs;
    }

    /**
     * Every number a live doc quotes for one kind of thing, with the
     * file and line it came from so a failure names the sentence to
     * fix rather than just the number.
     */
    private static List<String> claims(Pattern claim, List<Integer> found) throws IOException {
        List<String> where = new ArrayList<>();
        for (Path doc : liveDocs()) {
            List<String> lines = Files.readAllLines(doc);
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
        List<String> where = claims(Pattern.compile("(\\d+) (?:learning spaces|built in)"), found);
        assertThat(found).as("no live doc counts learning spaces at all — did the phrasing change?").isNotEmpty();
        assertThat(found)
                .as("stale learning-space counts (truth is %d): %s", spaces, where)
                .allMatch(n -> n == spaces);
    }

    @Test
    @DisplayName("every live doc that counts manifests agrees with WebProjectFactory")
    void manifestCount() throws Exception {
        // The MANIFESTS array is the door: a name in it opens that
        // checkout as a platform project. Count the literals between
        // the declaration and its closing brace.
        String factory = Files.readString(Path.of("..", "tools", "src", "main", "java", "org",
                "nmox", "studio", "tools", "npm", "WebProjectFactory.java"));
        int start = factory.indexOf("MANIFESTS = {");
        assertThat(start).as("the MANIFESTS array should exist").isGreaterThan(0);
        String array = factory.substring(start, factory.indexOf("};", start));
        long manifests = Pattern.compile("\"[^\"]+\"").matcher(array).results().count();
        assertThat(manifests).as("the factory should recognize manifests").isGreaterThan(40);

        List<Integer> found = new ArrayList<>();
        List<String> where = claims(Pattern.compile("(\\d+) manifest names"), found);
        assertThat(found)
                .as("stale manifest counts (truth is %d): %s", manifests, where)
                .allMatch(n -> n == manifests);
    }
}

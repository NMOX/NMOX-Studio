package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No file in the repository may carry a VCS conflict marker (v1.314.0).
 *
 * <p>Written because one did, for six releases. The v1.307.0 process
 * slip left {@code CHANGELOG.md} with a committed
 * {@code <<<<<<< HEAD} / {@code =======} / {@code >>>>>>>} triple
 * wrapping the entire top of the file, and it rode through v1.308
 * to v1.313 unnoticed — every one of those releases published a
 * changelog whose first visible line was a merge artifact, because
 * nothing in the build ever looked. The tests all passed: they read
 * SUBSTRINGS out of these documents, and a substring is still there
 * when a marker is sitting above it.
 *
 * <p>So this gate reads the shape rather than any content. It is
 * deliberately repo-wide (docs, sources, configs, scripts): a conflict
 * marker is never intentional anywhere, and a class of mistake that
 * survived six ships deserves a check that cannot be scoped past.
 */
class ConflictMarkerGateTest {

    /**
     * Built at runtime, never written as a literal — a gate that
     * contains its own forbidden pattern would either flag itself or
     * need an exception, and an exception is how the next real marker
     * hides.
     */
    private static final List<String> MARKERS = List.of(
            "<".repeat(7) + " ",
            "=".repeat(7),
            ">".repeat(7) + " ");

    private static final List<String> EXTENSIONS = List.of(
            ".java", ".md", ".xml", ".json", ".yml", ".yaml", ".sh",
            ".properties", ".txt", ".html", ".css", ".js", ".ts");

    @Test
    @DisplayName("no tracked text file carries a conflict marker")
    void noConflictMarkersAnywhere() throws IOException {
        Path root = Path.of("..").toRealPath();
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                // Windows walks yield backslash paths — normalize before
                // matching (the v1.63.2 class)
                String s = p.toString().replace('\\', '/');
                if (s.contains("/target/") || s.contains("/.")
                        || s.contains("/node_modules/")
                        || EXTENSIONS.stream().noneMatch(s::endsWith)) {
                    continue;
                }
                scanned++;
                int line = 0;
                for (String text : Files.readAllLines(p,
                        java.nio.charset.StandardCharsets.UTF_8)) {
                    line++;
                    for (String marker : MARKERS) {
                        // a marker is a whole line, or opens one: "======="
                        // alone is also a Markdown setext rule, so require
                        // the line to be EXACTLY that, and the angle forms
                        // to start the line
                        boolean hit = marker.startsWith("=")
                                ? text.equals(marker)
                                : text.startsWith(marker);
                        if (hit) {
                            offenders.add(
                                    root.relativize(p) + ":" + line + " " + text);
                        }
                    }
                }
            }
        }
        assertThat(offenders)
                .as("a committed conflict marker means a merge was resolved by"
                        + " hand and left half-done — the file is not what it"
                        + " claims to be, and every content test still passes"
                        + " because substrings survive")
                .isEmpty();
        assertThat(scanned)
                .as("the gate reached the repository (a filter that matches"
                        + " nothing would pass vacuously)")
                .isGreaterThan(200);
    }
}

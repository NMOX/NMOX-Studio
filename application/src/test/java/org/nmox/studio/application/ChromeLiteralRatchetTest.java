package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chrome-literal ratchet (v2.97.0, the l10n arc): a user-visible
 * English string built straight into a Swing sink is a string no
 * translation can reach. This gate counts the sinks that still take a
 * bare literal, per module, and fails the build when a module GROWS its
 * count — new UI text goes through a bundle. The pins are the measured
 * remainder at ship time; lower a pin when you localize more, never
 * raise one. The rack's device faceplates are out of the count by
 * decision (the panel vocabulary stays English — ledger 85).
 */
class ChromeLiteralRatchetTest {

    /** A Swing text sink handed a string literal, capturing the literal. */
    private static final Pattern LITERAL_SINK = Pattern.compile(
            "(?:\\bnew\\s+(?:javax\\.swing\\.)?J(?:Label|Button|CheckBox|RadioButton|MenuItem|ToggleButton)\\s*\\(\\s*"
            + "|\\.setToolTipText\\s*\\(\\s*"
            + "|\\.setText\\s*\\(\\s*"
            + "|StatusDisplayer\\.getDefault\\(\\)\\.setStatusText\\s*\\(\\s*"
            + "|\\.setAccessibleName\\s*\\(\\s*"
            + "|\\.setAccessibleDescription\\s*\\(\\s*"
            // a Swing Action's tooltip and name are set through putValue, not a
            // setter — the sink this gate was blind to until ledger 88 (v2.100.0)
            + "|putValue\\s*\\(\\s*(?:Action\\.)?(?:SHORT_DESCRIPTION|NAME)\\s*,\\s*)"
            + "\"((?:[^\"\\\\\\n]|\\\\.)*)\"");

    /**
     * Words, not furniture. A blank spacer, a clear, a glyph, a percentage
     * or a wordmark carries nothing to translate; only a literal holding a
     * run of letters is a sentence a user reads in English.
     */
    private static final Pattern HAS_WORDS = Pattern.compile("[A-Za-z]{2,}");

    /** Measured 2026-09-07 after the first extraction tranche; only ever lowered. */
    static final Map<String, Integer> PINNED = Map.ofEntries(
            Map.entry("core", 0),
            // the markup head of a composite whose text parts are data
            Map.entry("editor", 1),
            Map.entry("tools", 0),
            Map.entry("project", 0),
            // MissingDevice paints the faceplate's own LCD vocabulary
            Map.entry("rack", 2),
            Map.entry("apiclient", 0),
            Map.entry("dbstudio", 0),
            // two <html> heads of data-built composites
            Map.entry("web3", 2),
            Map.entry("infra", 0),
            // the wordmark on the Welcome
            Map.entry("ui", 1));

    static Map<String, Integer> measure() throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        for (String module : PINNED.keySet()) {
            Path src = Path.of("..", module, "src", "main", "java");
            int n = 0;
            if (Files.isDirectory(src)) {
                try (Stream<Path> files = Files.walk(src)) {
                    for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                        // the faceplates are the hardware panel — out by decision.
                        // Separators normalized first: a repo-scan gate that
                        // matches a bare "/" path skips nothing on the Windows
                        // lane, and the Windows lane is the binding measurement
                        // (this shipped as a windows-only red on PR #711 —
                        // rack counted 216 where the pin is 2).
                        String path = p.toString().replace('\\', '/');
                        if (module.equals("rack") && path.contains("/rack/devices/")) {
                            continue;
                        }
                        Matcher m = LITERAL_SINK.matcher(Files.readString(p));
                        while (m.find()) {
                            if (HAS_WORDS.matcher(m.group(1)).find()) {
                                n++;
                            }
                        }
                    }
                }
            }
            counts.put(module, n);
        }
        return counts;
    }

    @Test
    @DisplayName("no module grows its count of Swing sinks fed a bare literal — new UI text rides a bundle")
    void literalsOnlyGoDown() throws IOException {
        Map<String, Integer> measured = measure();
        List<String> grew = new ArrayList<>();
        for (Map.Entry<String, Integer> e : measured.entrySet()) {
            int pin = PINNED.get(e.getKey());
            if (e.getValue() > pin) {
                grew.add(e.getKey() + ": " + e.getValue() + " literal sinks, pinned " + pin);
            }
        }
        assertThat(grew).as("modules whose chrome literals grew (measured " + measured + ")").isEmpty();
    }
}

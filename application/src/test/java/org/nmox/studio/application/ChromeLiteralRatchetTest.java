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

    /** A Swing text sink handed a string literal. */
    private static final Pattern LITERAL_SINK = Pattern.compile(
            "\\bnew\\s+(?:javax\\.swing\\.)?J(?:Label|Button|CheckBox|RadioButton|MenuItem|ToggleButton)\\s*\\(\\s*\""
            + "|\\.setToolTipText\\s*\\(\\s*\""
            + "|\\.setText\\s*\\(\\s*\""
            + "|StatusDisplayer\\.getDefault\\(\\)\\.setStatusText\\s*\\(\\s*\""
            + "|\\.setAccessibleName\\s*\\(\\s*\""
            + "|\\.setAccessibleDescription\\s*\\(\\s*\"");

    /** Measured 2026-09-07 after the first extraction tranche; only ever lowered. */
    static final Map<String, Integer> PINNED = Map.of(
            "core", 0,
            "editor", 0,
            "tools", 0,
            "project", 0,
            "rack", 0,
            "apiclient", 0,
            "dbstudio", 0,
            "web3", 0,
            "infra", 0,
            "ui", 0);

    static Map<String, Integer> measure() throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        for (String module : PINNED.keySet()) {
            Path src = Path.of("..", module, "src", "main", "java");
            int n = 0;
            if (Files.isDirectory(src)) {
                try (Stream<Path> files = Files.walk(src)) {
                    for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                        // the faceplates are the hardware panel — out by decision
                        if (module.equals("rack") && p.toString().contains("/rack/devices/")) {
                            continue;
                        }
                        Matcher m = LITERAL_SINK.matcher(Files.readString(p));
                        while (m.find()) {
                            n++;
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

package org.nmox.studio.core.spi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * A pure core holds data and contracts, never sentences (ledger 89,
 * v2.101.0).
 *
 * <p>`core.spi` has no bundle and cannot have one — it is the seam every
 * module depends on, below the UI. So a sentence written here is a sentence
 * no translation can ever reach, and the two that were here reached a Swing
 * sink: {@code LiveRuns.tooltip()} assembled "Stop 3 running commands: …"
 * and {@code stoppedMessage()} assembled "Stopped: …", and the ■'s tooltip
 * and status line were English in twelve translated builds while every
 * bundle in the product was complete.
 *
 * <p>This is the half of the class that no sink-watching gate can see.
 * `ChromeLiteralRatchetTest` looks for a literal AT a Swing sink; these were
 * two modules away, inside a helper, and arrived at the sink as a variable.
 * The rule that does catch them is upstream and simple: <b>no prose in the
 * SPI at all</b>, wherever it was going.
 */
class SpiHoldsNoProseTest {

    /** A string literal, captured. */
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");

    /** A word: two or more letters. Three of them with a space is a sentence. */
    private static final Pattern WORD = Pattern.compile("[A-Za-z]{2,}");

    /**
     * The one blessed sentence, with its reason: KVASIR's neutral default
     * question is the PAYLOAD of a request to an AI, not chrome. It is never
     * painted; it is what gets asked when the user typed nothing. Translating
     * it would change the question, which is a different decision from
     * translating the product's own words.
     */
    private static final String BLESSED = "Explain this and tell me what to check first.";

    @Test
    @DisplayName("no sentence lives in core.spi — it has no bundle, so prose here can never be translated")
    void theSpiHoldsNoSentences() throws IOException {
        List<String> prose = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java", "org", "nmox", "studio", "core", "spi"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    String trimmed = line.strip();
                    // javadoc and comments describe the code; they are not shipped text
                    if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                        continue;
                    }
                    Matcher m = LITERAL.matcher(line);
                    while (m.find()) {
                        String value = m.group(1);
                        if (value.equals(BLESSED) || !value.contains(" ")) {
                            continue;
                        }
                        if (WORD.matcher(value).results().count() >= 3) {
                            prose.add(p.getFileName() + ":" + (i + 1) + " — \"" + value + "\"");
                        }
                    }
                }
            }
        }
        assertThat(prose)
                .as("sentences in core.spi: a pure core has no bundle, so this text can never be "
                        + "translated — return the data and let the consumer render it")
                .isEmpty();
    }
}

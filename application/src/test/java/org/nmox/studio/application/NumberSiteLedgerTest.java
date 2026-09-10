package org.nmox.studio.application;

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
 * Every locale-sensitive number in the product says who reads it
 * (ledger 93, v2.105.0) — the third of the family, after {@code Clocks}
 * (ledger 91) and {@code Collate} (ledger 92).
 *
 * <p>A bare {@code String.format("%.1f", x)} follows the reader's language,
 * which is RIGHT for anything painted on screen: a German should read
 * {@code 17,8 MB}. The product's display sites were measured correct before
 * this ledger existed and now say so by name through {@code Numbers.display}.
 *
 * <p>It is exactly wrong for a number written into a project file, a
 * command argument or a field an agent parses: a German locale writes
 * {@code 1,5} and the next reader gets a different number or an error. That
 * failure would be invisible to every English-speaking author and to every
 * bundle gate, because it is not a string — it is the shape of a value.
 *
 * <p>So the rule: a locale-sensitive conversion either routes through
 * {@code Numbers}, or names its locale explicitly. A new one fails the
 * build until someone decides which reader it serves.
 */
class NumberSiteLedgerTest {

    /**
     * A {@code String.format} whose conversion depends on the locale:
     * {@code %f}/{@code %e}/{@code %g} take a decimal separator and
     * {@code %,d} takes a grouping separator. {@code %d} and {@code %s}
     * alone are locale-independent for the types we pass them.
     */
    private static final Pattern SENSITIVE = Pattern.compile(
            "String\\.format\\(\\s*\"[^\"]*%[-0-9.#+ ]*(?:[feg]|,d)");

    /** An explicit locale as the first argument — the site has decided. */
    private static final Pattern DECIDED = Pattern.compile(
            "String\\.format\\(\\s*(?:java\\.util\\.)?Locale\\.");

    private static final List<String> MODULES = List.of("core", "editor", "tools", "project",
            "ui", "rack", "apiclient", "dbstudio", "web3", "infra");

    @Test
    @DisplayName("every locale-sensitive number format routes through Numbers or names its locale")
    void everyNumberIsClassified() throws IOException {
        List<String> unclassified = new ArrayList<>();
        int found = 0;
        for (Path p : sources()) {
            // the seam's own file: it DEFINES both forms, and its javadoc
            // quotes the very shape this ledger hunts (its first run said so)
            if (p.getFileName().toString().equals("Numbers.java")) {
                continue;
            }
            String body = read(p);
            String[] lines = body.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].strip();
                // a format quoted in prose is documentation, not a site
                if (line.startsWith("//") || line.startsWith("*")) {
                    continue;
                }
                Matcher m = SENSITIVE.matcher(lines[i]);
                while (m.find()) {
                    found++;
                    if (!DECIDED.matcher(lines[i]).find()) {
                        unclassified.add(p.getFileName() + ":" + (i + 1)
                                + " — a number nobody has said who reads");
                    }
                }
            }
        }
        // Zero is the DESIRED state here — every site was routed through
        // Numbers — so a count floor would guard nothing. What the floor
        // exists to catch is a blind census, and that is tested directly
        // below rather than inferred from a number.
        assertThat(found).as("sites seen but undecided are reported, not counted away")
                .isGreaterThanOrEqualTo(unclassified.size());
        assertThat(unclassified)
                .as("classify it: Numbers.display for a person, Numbers.stable for a record")
                .isEmpty();
    }

    @Test
    @DisplayName("the census can actually see — the patterns are proven on planted lines")
    void theCensusIsNotBlind() {
        // the count-gate law, applied where a count cannot serve: a regex
        // that matched nothing would make the ledger above pass forever
        assertThat(SENSITIVE.matcher("x = String.format(\"%.2f\", cost);").find())
                .as("a bare decimal format is the shape this ledger exists to find").isTrue();
        assertThat(SENSITIVE.matcher("x = String.format(\"%,d items\", n);").find())
                .as("a grouping separator moves with the reader too").isTrue();
        assertThat(SENSITIVE.matcher("x = String.format(\"%s/%d\", a, b);").find())
                .as("%s and %d alone are locale-independent for what we pass them").isFalse();
        assertThat(DECIDED.matcher("x = String.format(Locale.ROOT, \"%.2f\", cost);").find())
                .as("a named locale is a decision").isTrue();
        assertThat(DECIDED.matcher("x = String.format(java.util.Locale.ROOT, \"%.2f\", c);").find())
                .as("fully qualified counts too").isTrue();
    }

    @Test
    @DisplayName("the display sites are real callers, so the seam is not a payload without a gate")
    void theSeamHasCallers() throws IOException {
        int callers = 0;
        for (Path p : sources()) {
            if (p.getFileName().toString().equals("Numbers.java")) {
                continue;
            }
            String body = read(p);
            callers += body.split("Numbers\\.display\\(", -1).length - 1;
            callers += body.split("Numbers\\.stable\\(", -1).length - 1;
        }
        assertThat(callers)
                .as("a seam nobody calls is a payload without a gate — the v1.212.0 class")
                .isGreaterThanOrEqualTo(10);
    }

    /** CRLF-normalized: the Windows lane checks these files out with \r\n. */
    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
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

package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * Every clock in the product is classified by who reads it (ledger 91,
 * v2.104.0) — the spawn-ledger idiom applied to time.
 *
 * <p>A hand-rolled {@code ofPattern("HH:mm")} is a 24-hour clock shown to
 * everyone, which is wrong for any reader whose language does not write times
 * that way. We ship Hindi, and Hindi writes {@code 2:32 pm}; every Hindi user
 * had been reading a German clock. Externalizing strings was only half of
 * internationalization — this is the half where the SHAPE of a value depends
 * on the reader rather than the words.
 *
 * <p>But the opposite mistake is just as real: a filename, a log line, or a
 * field an agent parses must NOT move with the reader, or two people's
 * records stop lining up. So neither answer is right by default and each site
 * has to say which it is.
 *
 * <p>The rule: a date pattern in main sources lives in {@code Clocks}, or is
 * named here as STABLE with its reason. A new one fails the build until
 * someone decides — enumeration beats recollection.
 */
class ClockSiteLedgerTest {

    /** A hand-rolled date/time pattern. */
    private static final Pattern CLOCK =
            Pattern.compile("DateTimeFormatter\\.ofPattern\\(|new\\s+SimpleDateFormat\\(");

    private static final List<String> MODULES = List.of("core", "editor", "tools", "project",
            "ui", "rack", "apiclient", "dbstudio", "web3", "infra");

    /**
     * STABLE by decision: a record, not a reading. Each entry is the file, and
     * the value is why its clock must never move with the reader.
     */
    private static final Map<String, String> STABLE = Map.of(
            "Clocks.java",
            "the one home: it DEFINES both clocks",
            "Texts.java",
            "the Agent Port's text is read by an agent; a stamp that shifted per "
            + "reader would make two agents' transcripts disagree",
            "FlightRecorder.java",
            "the flight log is exported into bug reports, where a stable stamp is "
            + "comparable between the reporter's machine and the reader's",
            "BlackboxDevice.java",
            "a rack faceplate: the panel vocabulary stays fixed by decision (ledger 85)",
            "IrcLogger.java",
            "the on-disk transcript is a record; the on-SCREEN stamp beside it is "
            + "localized, which is exactly the split this ledger exists to make",
            "Screenshot.java",
            "a filename — it must sort, match and survive being copied between machines");

    @Test
    @DisplayName("every date pattern in the product is either Clocks or a named STABLE site")
    void everyClockIsClassified() throws IOException {
        List<String> unclassified = new ArrayList<>();
        int found = 0;
        for (Path p : sources()) {
            String body = Files.readString(p, StandardCharsets.UTF_8);
            Matcher m = CLOCK.matcher(body);
            while (m.find()) {
                found++;
                String file = p.getFileName().toString();
                if (!STABLE.containsKey(file)) {
                    unclassified.add(file + " — a clock nobody has said who reads");
                }
            }
        }
        assertThat(found).as("the census should find the product's clocks").isGreaterThan(4);
        assertThat(unclassified)
                .as("classify it: Clocks.display for a person, Clocks.stable for a record")
                .isEmpty();
    }

    @Test
    @DisplayName("every STABLE reason is written down, and the list holds no ghosts")
    void reasonsAreRealAndCurrent() throws IOException {
        List<String> ghosts = new ArrayList<>();
        List<String> reasonless = new ArrayList<>();
        List<String> files = sources().stream().map(p -> p.getFileName().toString()).toList();
        for (Map.Entry<String, String> e : STABLE.entrySet()) {
            if (!files.contains(e.getKey())) {
                ghosts.add(e.getKey());
            }
            if (e.getValue().length() < 25) {
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

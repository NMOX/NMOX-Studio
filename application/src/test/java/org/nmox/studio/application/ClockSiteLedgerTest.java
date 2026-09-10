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

    /**
     * The shape the first census could not see (v2.105.0): a {@code LocalDate}
     * handed to a string builder or concatenated renders through
     * {@code toString()}, which is ISO-8601 and therefore stable — no pattern
     * appears anywhere. That is invisible to {@link #CLOCK}, so the ledger
     * covered a MECHANISM rather than the population; a date reaching a
     * reader is a date reaching a reader however it was written.
     */
    private static final Pattern IMPLIED_DATE =
            Pattern.compile("(?:append|\\+)\\s*\\(?\\s*(?:start|end|today|yesterday|day|date)\\b(?!\\w)");

    private static final List<String> MODULES = List.of("core", "editor", "tools", "project",
            "ui", "rack", "apiclient", "dbstudio", "web3", "infra");

    /**
     * STABLE by decision: a record, not a reading. Each entry is the file, and
     * the value is why its clock must never move with the reader.
     */
    private static final Map<String, String> STABLE = Map.ofEntries(
            Map.entry("Clocks.java",
                    "the one home: it DEFINES both clocks"),
            Map.entry("Texts.java",
                    "the Agent Port's text is read by an agent; a stamp that shifted per "
            + "reader would make two agents' transcripts disagree"),
            Map.entry("FlightRecorder.java",
                    "the flight log is exported into bug reports, where a stable stamp is "
            + "comparable between the reporter's machine and the reader's"),
            Map.entry("BlackboxDevice.java",
                    "a rack faceplate: the panel vocabulary stays fixed by decision (ledger 85)"),
            Map.entry("IrcLogger.java",
                    "the on-disk transcript is a record; the on-SCREEN stamp beside it is "
            + "localized, which is exactly the split this ledger exists to make"),
            Map.entry("Screenshot.java",
                    "a filename — it must sort, match and survive being copied between machines"),
            Map.entry("SprintReport.java",
                    "a LocalDate rendered by toString: ISO-8601 by construction, and right "
            + "for a report pasted into a pull request where the reader may be anywhere"),
            Map.entry("StandupReport.java",
                    "the standup is markdown someone pastes into a channel other people read; "
            + "an ISO date is unambiguous to all of them, a localized one is not"),
            Map.entry("BoardStats.java",
                    "calendar-day bucketing, never rendered — the dates are compared, and the "
            + "labels beside them come from bundles")
    );

    @Test
    @DisplayName("every date pattern in the product is either Clocks or a named STABLE site")
    void everyClockIsClassified() throws IOException {
        List<String> unclassified = new ArrayList<>();
        int found = 0;
        int implied = 0;
        for (Path p : sources()) {
            String body = Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
            String file = p.getFileName().toString();
            Matcher m = CLOCK.matcher(body);
            while (m.find()) {
                found++;
                if (!STABLE.containsKey(file)) {
                    unclassified.add(file + ":" + line(body, m.start())
                            + " — a clock nobody has said who reads");
                }
            }
            // the second shape, added v2.105.0: a date rendered without any
            // pattern at all. Only files that actually hold a date matter —
            // the name alone would match arithmetic anywhere.
            if (!body.contains("java.time.LocalDate") && !body.contains("import java.time.LocalDate")) {
                continue;
            }
            Matcher d = IMPLIED_DATE.matcher(body);
            while (d.find()) {
                implied++;
                if (!STABLE.containsKey(file)) {
                    unclassified.add(file + ":" + line(body, d.start())
                            + " — a date nobody has said who reads");
                }
            }
        }
        assertThat(found).as("the census should find the product's clocks").isGreaterThan(4);
        assertThat(implied)
                .as("the widened census must actually see the pattern-less date shape")
                .isGreaterThan(0);
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

    /** 1-based line number of an offset, for a finding that names its place. */
    private static int line(String body, int offset) {
        int n = 1;
        for (int i = 0; i < offset && i < body.length(); i++) {
            if (body.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
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

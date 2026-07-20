package org.nmox.studio.rack.engine;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.FlightRecorder.Kind;

import static org.assertj.core.api.Assertions.assertThat;

/** The fold logic, on an injected clock - no bus, no waiting. */
class FlightRecorderTest {

    private AtomicLong now;
    private FlightRecorder rec;

    @BeforeEach
    void fresh() {
        now = new AtomicLong(1_000_000);
        rec = new FlightRecorder(now::get);
    }

    private void tick(long ms) {
        now.addAndGet(ms);
    }

    @Test
    @DisplayName("Launch + exit pair into a run with a duration")
    void pairsLaunchAndExit() {
        rec.line("FORGE", "$ npm run build", false);
        tick(1_200);
        rec.line("FORGE", "[exit 0]", false);

        var timeline = rec.timeline();
        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).kind()).isEqualTo(Kind.LAUNCH);
        assertThat(timeline.get(1).kind()).isEqualTo(Kind.EXIT_OK);
        assertThat(timeline.get(1).durationMs()).isEqualTo(1_200);
        assertThat(rec.last().device()).isEqualTo("FORGE");
    }

    @Test
    @DisplayName("Failures and error lines land on the tape; stack traces stay a sample")
    void recordsFailuresSampled() {
        rec.line("VERITAS", "$ npm test", false);
        for (int i = 0; i < 40; i++) {
            rec.line("VERITAS", "Error: assertion " + i, true);
        }
        tick(800);
        rec.line("VERITAS", "[exit 1]", true);

        long errors = rec.timeline().stream().filter(e -> e.kind() == Kind.ERROR).count();
        assertThat(errors).as("error sample cap").isEqualTo(5);
        assertThat(rec.last().kind()).isEqualTo(Kind.EXIT_FAIL);
        assertThat(rec.errorsSince(0)).hasSize(6); // 5 samples + the failed exit
    }

    @Test
    @DisplayName("The slow creep: a run far past its average raises the flag")
    void detectsSlowCreep() {
        for (long ms : new long[]{1000, 1100, 1050}) {
            rec.line("FORGE", "$ npm run build", false);
            tick(ms);
            rec.line("FORGE", "[exit 0]", false);
        }
        assertThat(rec.slowCreep()).isNull();

        rec.line("FORGE", "$ npm run build", false);
        tick(3_400);
        rec.line("FORGE", "[exit 0]", false);

        assertThat(rec.slowCreep()).isEqualTo("FORGE");
        var stats = rec.statistics().get("FORGE");
        assertThat(stats.lastMs()).isEqualTo(3_400);
        assertThat(stats.creeping()).isTrue();
    }

    @Test
    @DisplayName("The tape is a ring: capacity bounds memory")
    void ringBufferCaps() {
        for (int i = 0; i < 3_000; i++) {
            rec.line("PING", "$ run " + i, false);
        }
        assertThat(rec.timeline()).hasSize(2_000);
    }

    @Test
    @DisplayName("Export reads like a log a human would paste into a bug report")
    void exportIsReadable() {
        rec.line("FORGE", "$ npm run build", false);
        tick(1_500);
        rec.line("FORGE", "[exit 0]", false);
        String log = rec.export();
        assertThat(log).contains("FORGE").contains("LAUNCH").contains("npm run build")
                .contains("EXIT_OK").contains("(1.5s)");
    }

    @Test
    @DisplayName("The tape survives the JVM: journal writes and reloads")
    void journalRoundTrips(@org.junit.jupiter.api.io.TempDir java.io.File dir) {
        java.io.File journal = new java.io.File(dir, "flight.jsonl");

        FlightRecorder first = new FlightRecorder(now::get);
        first.attachJournal(journal);
        first.line("FORGE", "$ npm run build", false);
        tick(1_200);
        first.line("FORGE", "[exit 0]", false);
        // the journal append rides its own lane now (off the recorder monitor);
        // drain it before reading the file back
        FlightRecorder.awaitJournalIdle();
        assertThat(journal).exists();

        FlightRecorder reborn = new FlightRecorder(now::get);
        reborn.attachJournal(journal);
        assertThat(reborn.timeline()).hasSize(2);
        assertThat(reborn.timeline().get(1).kind()).isEqualTo(Kind.EXIT_OK);
        assertThat(reborn.timeline().get(1).durationMs()).isEqualTo(1_200);
    }

    @Test
    @DisplayName("Journal disk I/O rides JOURNAL_RP, never inline under the recorder monitor")
    void journalWritesOffTheMonitor() throws Exception {
        // Source gate for the rack-engine review's MED-1: record() must hand the
        // append to JOURNAL_RP, not call appendToJournal(e) inline — otherwise a
        // slow/full disk stalls every device's output pump (all funnel through
        // the recorder monitor) and blocks EDT readers behind a pump's write.
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/rack/engine/FlightRecorder.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        int m = src.indexOf("private void record(Event e)");
        assertThat(m).as("record() exists").isPositive();
        String body = src.substring(m, src.indexOf("\n    }", m));
        assertThat(body)
                .as("the append is posted to the journal lane")
                .contains("JOURNAL_RP.post(() -> appendToJournal(e))");
        assertThat(body)
                .as("the append is NOT run inline under the caller's monitor")
                .doesNotContain("        appendToJournal(e);");
    }

    @Test
    @DisplayName("A corrupt journal line is skipped, not fatal")
    void corruptJournalLineSkipped(@org.junit.jupiter.api.io.TempDir java.io.File dir) throws Exception {
        java.io.File journal = new java.io.File(dir, "flight.jsonl");
        java.nio.file.Files.writeString(journal.toPath(),
                FlightRecorder.eventToJson(new FlightRecorder.Event(1, "A", Kind.LAUNCH, "x", -1))
                + "\n{garbage\n");
        FlightRecorder rec2 = new FlightRecorder(now::get);
        rec2.attachJournal(journal);
        assertThat(rec2.timeline()).hasSize(1);
    }

    @Test
    @DisplayName("The journal rotates at its cap, keeping the newer half, still loadable")
    void journalRotates(@org.junit.jupiter.api.io.TempDir java.io.File dir) {
        java.io.File journal = new java.io.File(dir, "flight.jsonl");
        FlightRecorder writer = new FlightRecorder(now::get);
        writer.attachJournal(journal);

        // ~20k events at ~90 bytes/line crosses the 1.5MB cap and rotates
        for (int i = 0; i < 20_000; i++) {
            tick(1);
            writer.line("FORGE", "$ build-" + i, false);
        }
        // appends + rotation ride JOURNAL_RP now; drain before reading the file
        FlightRecorder.awaitJournalIdle();

        assertThat(journal.length())
                .as("rotation must keep the file near its cap, not unbounded")
                .isLessThan(2_000_000);

        FlightRecorder reborn = new FlightRecorder(now::get);
        reborn.attachJournal(journal);
        assertThat(reborn.timeline()).isNotEmpty();
        var lastLoaded = reborn.timeline().get(reborn.timeline().size() - 1);
        assertThat(lastLoaded.text())
                .as("the newest event survives rotation")
                .isEqualTo("build-19999");
    }
}

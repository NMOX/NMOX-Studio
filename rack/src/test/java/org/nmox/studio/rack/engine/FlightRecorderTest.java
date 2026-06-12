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
}

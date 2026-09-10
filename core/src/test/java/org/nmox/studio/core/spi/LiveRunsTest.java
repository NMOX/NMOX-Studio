package org.nmox.studio.core.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiveRunsTest {

    @AfterEach
    void drain() {
        LiveRuns.stopAll();
    }

    @Test
    @DisplayName("Runs are live from add until remove, in spawn order; listeners follow every change")
    void lifecycle() {
        AtomicInteger notified = new AtomicInteger();
        Runnable l = notified::incrementAndGet;
        LiveRuns.addListener(l);
        try {
            LiveRuns.add(new LiveRuns.Run("a", "Run — one", () -> { }));
            LiveRuns.add(new LiveRuns.Run("b", "Build — two", () -> { }));
            assertThat(LiveRuns.live()).extracting(LiveRuns.Run::id).containsExactly("a", "b");
            LiveRuns.remove("a");
            assertThat(LiveRuns.live()).extracting(LiveRuns.Run::id).containsExactly("b");
            LiveRuns.remove("zzz");
            assertThat(notified.get()).as("two adds + one real remove; a no-op remove is silent").isEqualTo(3);
        } finally {
            LiveRuns.removeListener(l);
        }
    }

    @Test
    @DisplayName("stopAll kills EVERY live run through its killer and forgets them all — the ■ never leaves a survivor")
    void stopAllKillsEveryone() {
        List<String> killed = new ArrayList<>();
        LiveRuns.add(new LiveRuns.Run("a", "Run — one", () -> killed.add("a")));
        LiveRuns.add(new LiveRuns.Run("b", "Run — two", () -> killed.add("b")));
        List<LiveRuns.Run> stopped = LiveRuns.stopAll();
        assertThat(killed).containsExactly("a", "b");
        assertThat(stopped).extracting(LiveRuns.Run::label).containsExactly("Run — one", "Run — two");
        assertThat(LiveRuns.live()).as("nothing is live after a stop").isEmpty();
        assertThat(LiveRuns.stopAll()).as("a second press stops nothing and says so").isEmpty();
    }

    @Test
    @DisplayName("a withdrawal that arrives before the add leaves a tombstone: the late add is dropped, no phantom (v2.71.0)")
    void withdrawalBeforeAddIsATombstone() {
        LiveRuns.remove("failed-launch#1");
        assertThat(LiveRuns.add(new LiveRuns.Run("failed-launch#1", "npm install — x", () -> { })))
                .as("the exit came first: the run was never live").isFalse();
        assertThat(LiveRuns.live()).isEmpty();
        // a fresh id is unaffected, and a tombstone is spent once
        assertThat(LiveRuns.add(new LiveRuns.Run("failed-launch#2", "ok", () -> { }))).isTrue();
        assertThat(LiveRuns.add(new LiveRuns.Run("failed-launch#1", "again", () -> { }))).as("spent").isTrue();
        assertThat(LiveRuns.live()).extracting(LiveRuns.Run::id).containsExactly("failed-launch#2", "failed-launch#1");
    }

    @Test
    @DisplayName("a user's Stop is remembered for the exit handler, once; a natural exit is not (v2.73.0 review)")
    void stoppedByUserIsRemembered() {
        LiveRuns.add(new LiveRuns.Run("u1", "npm install — x", () -> { }));
        LiveRuns.add(new LiveRuns.Run("u2", "Focused test: adds", () -> { }));
        LiveRuns.add(new LiveRuns.Run("u3", "Run — x", () -> { }));
        LiveRuns.stop("u1");
        LiveRuns.stopAll();
        assertThat(LiveRuns.wasStoppedByUser("u1")).as("stop(id)").isTrue();
        assertThat(LiveRuns.wasStoppedByUser("u1")).as("consumed").isFalse();
        assertThat(LiveRuns.wasStoppedByUser("u2")).as("stopAll").isTrue();
        assertThat(LiveRuns.wasStoppedByUser("u3")).isTrue();
        LiveRuns.add(new LiveRuns.Run("u4", "natural", () -> { }));
        LiveRuns.remove("u4");
        assertThat(LiveRuns.wasStoppedByUser("u4")).as("a natural exit was not a user stop").isFalse();
    }

    @Test
    @DisplayName("a live run knows since when; a withdrawn one forgets (v2.73.0)")
    void startedAtAndSince() {
        LiveRuns.clockForTest(() -> 1_000_000L);
        try {
            LiveRuns.add(new LiveRuns.Run("s1", "npm run dev — shop", () -> { }));
        } finally {
            LiveRuns.clockForTest(null);
        }
        assertThat(LiveRuns.startedAt("s1")).isEqualTo(1_000_000L);
        java.util.Locale was = java.util.Locale.getDefault();
        try {
            // the clock a person reads follows their language (v2.104.0):
            // a 24-hour locale and a 12-hour one, from the same instant
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertThat(LiveRuns.sinceTime(1_000_000L, java.time.ZoneId.of("UTC"))).isEqualTo("00:16");
            java.util.Locale.setDefault(java.util.Locale.US);
            // NOT compared to a literal: CLDR separates the day period with a
            // NARROW NO-BREAK SPACE (U+202F), which is invisible in a diff and
            // makes "12:16 AM" != "12:16 AM" for reasons no one can see
            assertThat(LiveRuns.sinceTime(1_000_000L, java.time.ZoneId.of("UTC")))
                    .as("the US writes the same instant with a day period")
                    .startsWith("12:16").endsWith("AM");
        } finally {
            java.util.Locale.setDefault(was);
        }
        assertThat(LiveRuns.sinceTime(-1L, java.time.ZoneId.of("UTC"))).isEmpty();
        LiveRuns.remove("s1");
        assertThat(LiveRuns.startedAt("s1")).as("gone with the run").isEqualTo(-1L);
        assertThat(LiveRuns.sinceTime("s1")).isEmpty();
    }

    @Test
    @DisplayName("the start time is DATA in every zone and hour — a bundle argument is never prose (ledger 88, v2.100.0)")
    void sinceTimeIsDataNotProse() {
        // this is the whole contract: three surfaces hand this value to a
        // bundle as {0}, so a single letter here is a letter that no
        // translation can reach. It shipped as "since HH:mm" for seven
        // releases and read «正在运行 since 14:32» in Chinese.
        for (String zone : new String[] {"UTC", "America/Denver", "Asia/Kolkata", "Pacific/Kiritimati"}) {
            java.time.ZoneId z = java.time.ZoneId.of(zone);
            for (long hour = 0; hour < 24; hour++) {
                String v = LiveRuns.sinceTime(hour * 3_600_000L + 61_000L, z);
                // DATA, in whatever shape the reader's language writes a time:
                // digits and separators, plus at most a short day-period mark
                // (AM/PM, 2 letters). What must never appear is a WORD — that
                // is what "since" was, and what no translation could reach.
                assertThat(v).as("%s at hour %d has digits", zone, hour).containsPattern("\\d");
                assertThat(v).as("%s at hour %d carries a word, not just a time", zone, hour)
                        .doesNotContainPattern("[\\p{L}]{3,}");
            }
        }
        java.util.Locale was = java.util.Locale.getDefault();
        try {
            for (String tag : new String[] {"en-US", "uk", "de", "hi", "zh", "pl", "vi"}) {
                java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag));
                assertThat(LiveRuns.sinceTime(50_000_000L, java.time.ZoneId.of("UTC")))
                        .as("%s writes a time, never a word", tag)
                        .containsPattern("\\d").doesNotContainPattern("[\\p{L}]{3,}");
            }
        } finally {
            java.util.Locale.setDefault(was);
        }
        assertThat(LiveRuns.sinceTime(-1L, java.time.ZoneId.of("UTC")))
                .as("not live: empty, so the caller picks the wordless message").isEmpty();
    }

    @Test
    @DisplayName("stop(id) kills exactly one run, forgets it, and tells the listeners (v2.70.0)")
    void stopOne() {
        java.util.List<String> killed = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger notified = new java.util.concurrent.atomic.AtomicInteger();
        Runnable l = notified::incrementAndGet;
        LiveRuns.addListener(l);
        try {
            LiveRuns.add(new LiveRuns.Run("a", "npm run dev — p", () -> killed.add("a")));
            LiveRuns.add(new LiveRuns.Run("b", "npm run test — p", () -> killed.add("b")));
            int before = notified.get();
            assertThat(LiveRuns.stop("a")).isNotNull();
            assertThat(killed).containsExactly("a");
            assertThat(LiveRuns.live()).extracting(LiveRuns.Run::id).containsExactly("b");
            assertThat(notified.get()).isEqualTo(before + 1);
            assertThat(LiveRuns.stop("nope")).as("no such run: nothing killed, nobody told").isNull();
            assertThat(notified.get()).isEqualTo(before + 1);
        } finally {
            LiveRuns.removeListener(l);
        }
    }

    @Test
    @DisplayName("a label that begins with <html> can never reach a platform JLabel/JMenuItem as markup (v2.70.0)")
    void markupLeadingLabelIsSetOff() {
        LiveRuns.Run r = new LiveRuns.Run("x", "<html><img src='http://evil/x'>", () -> { });
        assertThat(r.label()).startsWith(" <html>");
        assertThat(new LiveRuns.Run("y", "<HTML>shout", () -> { }).label()).startsWith(" <HTML>");
        assertThat(new LiveRuns.Run("z", "Run — <html>inside", () -> { }).label())
                .as("only the LEADING position is the sniff (BasicHTML.isHTMLString, decompiled)")
                .isEqualTo("Run — <html>inside");
    }
}

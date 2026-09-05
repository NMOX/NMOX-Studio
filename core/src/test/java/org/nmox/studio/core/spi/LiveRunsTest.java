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
    @DisplayName("The status line after ■: every stopped label, or that nothing was running")
    void message() {
        assertThat(LiveRuns.stoppedMessage(List.of())).isEqualTo("Nothing is running");
        assertThat(LiveRuns.stoppedMessage(List.of(new LiveRuns.Run("a", "Run — one", () -> { }),
                new LiveRuns.Run("b", "Build — two", () -> { })))).isEqualTo("Stopped: Run — one, Build — two");
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

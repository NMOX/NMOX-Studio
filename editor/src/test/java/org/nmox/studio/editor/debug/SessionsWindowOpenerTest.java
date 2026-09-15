package org.nmox.studio.editor.debug;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Sessions window opens itself for a run with more than one session
 * (v2.159.0) — the rule, and the wiring that makes the rule reachable: the
 * opener registers from the one launch every debug door runs through
 * (right-click, Debug File, Debug Main Project), never at boot.
 */
class SessionsWindowOpenerTest {

    @Test
    @DisplayName("one session is the platform's own case and opens nothing; a second session opens the window")
    void shouldOpenOnlyForASecondSession() {
        assertThat(SessionsWindowOpener.shouldOpen(0)).isFalse();
        assertThat(SessionsWindowOpener.shouldOpen(1)).isFalse();
        assertThat(SessionsWindowOpener.shouldOpen(2)).isTrue();
        assertThat(SessionsWindowOpener.shouldOpen(3)).isTrue();
    }

    @Test
    @DisplayName("the opener names the platform's Sessions window by its layer id")
    void shouldNameThePlatformsSessionsWindow() {
        assertThat(SessionsWindowOpener.SESSIONS_WINDOW).isEqualTo("sessionsView");
    }

    @Test
    @DisplayName("the one launch installs the opener before it posts the spawn, so every door reaches it")
    void shouldBeInstalledByTheLaunch() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/debug/DapDebugAction.java"));
        int launch = source.indexOf("static void launch(File file, String mime)");
        // the STATEMENT, at the start of a line — a commented-out call
        // ("// SessionsWindowOpener.install();") survived the first cut of
        // this gate, which matched the substring wherever it sat
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?m)^\\s*SessionsWindowOpener\\.install\\(\\);")
                .matcher(source);
        int install = m.find(launch) ? m.start() : -1;
        int post = source.indexOf("RP.post(", launch);
        assertThat(launch).as("the shared launch exists").isPositive();
        assertThat(install).as("launch installs the opener (as a statement, not a comment)").isPositive();
        assertThat(install).as("installed before the spawn is posted").isLessThan(post);
    }
}

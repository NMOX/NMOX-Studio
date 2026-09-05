package org.nmox.studio.editor.testing.explorer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.LiveRuns;

import static org.assertj.core.api.Assertions.assertThat;

/** The Tests window's Stop takes down test runs and nothing else (v2.73.0). */
class TestRunsStopTest {

    @AfterEach
    void drain() {
        LiveRuns.stopAll();
    }

    @Test
    @DisplayName("only focused-test runs count, and Stop kills exactly those")
    void onlyTestRuns() {
        List<String> killed = new ArrayList<>();
        LiveRuns.add(new LiveRuns.Run("ide-run:/p#1", "Run — shop", () -> killed.add("run")));
        assertThat(TestRunsStop.anyLive()).as("a dev server is not a test run").isFalse();
        LiveRuns.add(new LiveRuns.Run("focused-test:/p/a.test.js#2", "Focused test: adds", () -> killed.add("t1")));
        LiveRuns.add(new LiveRuns.Run("focused-test:/p/b.test.js#3", "Focused test: subtracts", () -> killed.add("t2")));
        assertThat(TestRunsStop.anyLive()).isTrue();
        assertThat(TestRunsStop.stopAll()).isEqualTo(2);
        assertThat(killed).containsExactlyInAnyOrder("t1", "t2");
        assertThat(LiveRuns.live()).extracting(LiveRuns.Run::id).as("the dev server survived").containsExactly("ide-run:/p#1");
        assertThat(TestRunsStop.stopAll()).isZero();
    }

    @Test
    @DisplayName("the window follows LiveRuns while showing and lets go when hidden; the button is named")
    void wiring() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/testing/explorer/TestsExplorerTopComponent.java"));
        int showing = src.indexOf("protected void componentShowing()");
        int hidden = src.indexOf("protected void componentHidden()");
        assertThat(src.indexOf("LiveRuns.addListener(runsListener)")).isGreaterThan(showing).isLessThan(hidden);
        assertThat(src.indexOf("LiveRuns.removeListener(runsListener)")).isGreaterThan(hidden);
        assertThat(src).contains("setAccessibleName(\"Stop running test\")").contains("TestRunsStop.stopAll()");
        String lane = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/testing/RunFocusedTestAction.java"));
        assertThat(lane).as("the lane's ids carry the prefix the Stop selects on").contains("\"" + TestRunsStop.PREFIX + "\"");
    }
}

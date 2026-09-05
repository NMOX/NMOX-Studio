package org.nmox.studio.project;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.LiveRuns;
import org.nmox.studio.core.spi.LiveServings;

import static org.assertj.core.api.Assertions.assertThat;

/** The Workbench's RUNNING rows (v2.73.0): the join of the ■'s registry and the ⇄ chip's, and the window's wiring. */
class WorkbenchRunningTest {

    private static LiveRuns.Run run(String id, String label) {
        return new LiveRuns.Run(id, label, () -> { });
    }

    private static LiveServings.Serving serving(String id, String title, String url) {
        return new LiveServings.Serving(id, title, url, LiveServings.Kind.WEB, new File("/tmp/p"));
    }

    @Test
    @DisplayName("runs lead in spawn order, each carrying the URL of the serving that shares its id; unowned servings follow")
    void joinByIdThenBareServings() {
        List<WorkbenchRunning.Row> rows = WorkbenchRunning.rows(
                List.of(run("ide-run:/p#1", "Run — shop"), run("npm-run:/p#2", "npm run test — shop")),
                List.of(serving("SURGE@1", "SURGE", "http://localhost:5173/"),
                        serving("ide-run:/p#1", "Run — shop", "http://localhost:3000/")));
        assertThat(rows).extracting(WorkbenchRunning.Row::title)
                .containsExactly("Run — shop", "npm run test — shop", "SURGE");
        assertThat(rows.get(0).url()).isEqualTo("http://localhost:3000/");
        assertThat(rows.get(0).stoppable()).isTrue();
        assertThat(rows.get(1).url()).as("a test run serves nothing").isNull();
        assertThat(WorkbenchRunning.subtitle(rows.get(1), "")).isEqualTo("running");
        assertThat(WorkbenchRunning.subtitle(rows.get(1), "since 10:41")).isEqualTo("running since 10:41");
        assertThat(WorkbenchRunning.subtitle(rows.get(0), "since 10:41")).isEqualTo("http://localhost:3000/  since 10:41");
        assertThat(rows.get(2).stoppable()).as("a rack device's server has its own STOP").isFalse();
        assertThat(rows.get(2).openable()).isTrue();
        assertThat(WorkbenchRunning.rows(List.of(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("a rack device's run owns the serving its bus announced (device:<bus>#n ↔ bus), so it is one row, not two (v2.74.0)")
    void deviceRunOwnsItsBusServing() {
        assertThat(WorkbenchRunning.owns("device:SOLDER#3", "SOLDER")).isTrue();
        assertThat(WorkbenchRunning.owns("device:SOLDER#3", "SOLDER·2")).as("a sibling bus").isFalse();
        assertThat(WorkbenchRunning.owns("ide-run:/p#1", "ide-run:/p#1")).isTrue();
        assertThat(WorkbenchRunning.owns("ide-run:/p#1", "SOLDER")).isFalse();
        List<WorkbenchRunning.Row> rows = WorkbenchRunning.rows(
                List.of(run("device:SOLDER#3", "SOLDER — npx")),
                List.of(serving("SOLDER", "SOLDER", "http://localhost:8080/")));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).url()).isEqualTo("http://localhost:8080/");
        assertThat(rows.get(0).stoppable()).isTrue();
    }

    @Test
    @DisplayName("the window: RUNNING is painted first, follows both registries symmetrically, and every row's Stop is a real button on LiveRuns.stop")
    void windowWiring() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/project/ProjectExplorerTopComponent.java"));
        int refresh = src.indexOf("private void refresh()");
        assertThat(src.indexOf("addRunning();", refresh)).as("RUNNING leads the page")
                .isGreaterThan(refresh).isLessThan(src.indexOf("addOpenFiles();", refresh));
        int opened = src.indexOf("public void componentOpened()");
        int closed = src.indexOf("public void componentClosed()");
        assertThat(src.indexOf("LiveRuns.addListener(runsListener)")).isGreaterThan(opened).isLessThan(closed);
        assertThat(src.indexOf("liveServings.addListener(servingsListener)")).isGreaterThan(opened).isLessThan(closed);
        assertThat(src.indexOf("LiveRuns.removeListener(runsListener)")).isGreaterThan(closed);
        assertThat(src.indexOf("liveServings.removeListener(servingsListener)")).isGreaterThan(closed);
        // unit 6 moved the buttons through flatButton(text, accessibleName): the
        // gate reads the calls that name them (the behavioral test presses them)
        assertThat(src).contains("flatButton(\"Stop\", \"Stop \" + r.title())")
                .contains("flatButton(\"Open\", \"Open \" + r.url() + \" in the Browser\")")
                .contains("LiveRuns.stop(r.runId())")
                .contains("ServingLinks.open(r.url())");
    }
}

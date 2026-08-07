package org.nmox.studio.project;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Workbench forgets on request (v1.288.0, the night Workbench
 * walk). The organize sweep gave every studio's named-artifact list a
 * removal verb; the home base's RECENT FILES and PROJECTS rows were the
 * holdout — a learning space sat in the daily list for the life of the
 * install, and right-click offered nothing.
 */
class WorkbenchForgetTest {

    @Test
    @DisplayName("drop is the pure inverse of push")
    void dropRemovesExactlyOneEntry() {
        String csv = "/a/x.js\n/b/y.md\n/c/z.ts";
        assertThat(RecentFiles.drop(csv, "/b/y.md"))
                .isEqualTo("/a/x.js\n/c/z.ts");
        assertThat(RecentFiles.drop(csv, "/nope"))
                .as("forgetting an absent path is a no-op, not corruption")
                .isEqualTo(csv);
        assertThat(RecentFiles.drop("/only", "/only"))
                .as("the last entry leaves an empty trail")
                .isEmpty();
        assertThat(RecentFiles.drop(RecentFiles.push("", "/a", 5), "/a")).isEmpty();
    }

    @Test
    @DisplayName("both sections wire the gesture; the aimed project is exempt")
    void rowsWireTheGesture() throws Exception {
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "project", "ProjectExplorerTopComponent.java"),
                StandardCharsets.UTF_8);

        assertThat(src)
                .as("a recent file row must offer Forget")
                .contains("RecentFiles.forget(file, refreshCoalescer::request)");
        assertThat(src)
                .as("a project row must offer Forget through the SPI")
                .contains("a.forgetRecentProject(dir)");
        assertThat(src)
                .as("the aimed project re-adds itself to the list head, so a"
                        + " Forget there would be a lie — its row must carry"
                        + " no menu")
                .contains("aimed ? null :");
        assertThat(src)
                .as("the menu item must say what it does NOT do")
                .contains("stays on disk");
        assertThat(src)
                .as("children must inherit the row popup or right-clicking"
                        + " the title itself shows nothing")
                .contains("setInheritsPopupMenu(true)");
    }

    @Test
    @DisplayName("the forget write completes before the refresh callback")
    void refreshFollowsTheWrite() throws Exception {
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "project", "RecentFiles.java"),
                StandardCharsets.UTF_8);
        int put = src.indexOf("drop(prefs.get(PREF_KEY");
        int done = src.indexOf("onDone.run()");
        assertThat(put).isPositive();
        assertThat(done)
                .as("a refresh fired before the pref write would repaint the"
                        + " row the user just forgot")
                .isGreaterThan(put);
    }
}

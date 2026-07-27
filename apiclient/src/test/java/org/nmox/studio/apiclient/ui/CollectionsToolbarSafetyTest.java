package org.nmox.studio.apiclient.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The collections-panel button laws, source-gated. The v1.167.0 and
 * v1.182.0 gauntlets both caught the same class — a single-row
 * JToolBar clips without a chevron, hiding Delete at narrow widths —
 * and v1.192.0 closed it structurally: a 2x2 grid can never clip, and
 * Delete additionally lives in the tree's context menu and on the
 * Delete key. The key binding is what makes the confirm law
 * load-bearing: with no undo in API Studio, a stray keypress deleting
 * a non-empty collection would be silent data loss.
 */
class CollectionsToolbarSafetyTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java"));
    }

    @Test
    @DisplayName("The collection buttons live in a grid, never a clipping toolbar")
    void gridNotToolbar() throws Exception {
        String s = source();
        int start = s.indexOf("GridLayout(2, 2");
        assertThat(start).as("the 2x2 grid exists").isGreaterThan(-1);
        // the narrow side panel must not regress to a JToolBar between
        // the grid and where the buttons join it (the full-width
        // environment bar at the top of the window is a different,
        // non-clipping surface and stays a toolbar legitimately)
        String panel = s.substring(start, s.indexOf("tools.add(addCol)", start));
        assertThat(panel).doesNotContain("new JToolBar()");
    }

    @Test
    @DisplayName("Delete is reachable from the context menu and the keyboard")
    void deleteHasPlatformHomes() throws Exception {
        String s = source();
        assertThat(s).contains("setComponentPopupMenu");
        assertThat(s).contains("getKeyStroke(\"DELETE\")");
        assertThat(s).contains("getKeyStroke(\"BACK_SPACE\")");
    }

    @Test
    @DisplayName("Deleting a non-empty collection confirms with the safe default")
    void collectionDeleteConfirmsSafely() throws Exception {
        String s = source();
        int delete = s.indexOf("private void deleteSelected()");
        int end = s.indexOf("\n    }", delete);
        String body = s.substring(delete, end);
        assertThat(body)
                .as("the v1.98.0 idiom: full NotifyDescriptor with NO_OPTION default")
                .contains("NotifyDescriptor.NO_OPTION");
        assertThat(body).contains("c.requests.isEmpty()");
    }
}

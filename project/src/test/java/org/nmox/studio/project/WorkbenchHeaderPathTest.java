package org.nmox.studio.project;

import java.io.File;
import javax.swing.JLabel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Workbench header's path line (3.1.0): a deep path must not set the
 * dock's width. The walk of {@code --aim} aimed a folder under a long temp
 * path and the left dock took half the window, because the unshortened
 * label's preferred width became the mode's width on a fresh layout.
 */
class WorkbenchHeaderPathTest {

    @Test
    @DisplayName("a deep path never widens the dock, and the whole path stays on the tooltip")
    void deepPathKeepsItsEndsAndItsWidth() {
        String deep = "/private/tmp/claude-501/-Users-david-vcs-git-github-nmox-NMOX-Studio/"
                + "791bf260-044d-4624-a2fc-7dcbfe17cd1f/scratchpad/cli/notes";
        // absolute as THIS platform spells it (a drive letter on Windows)
        String full = new File(deep).getAbsolutePath();
        JLabel label = ProjectExplorerTopComponent.headerPath(new File(deep));
        assertThat(label.getPreferredSize().width)
                .isLessThanOrEqualTo(org.nmox.studio.core.util.PathLabel.PREFERRED_CAP);
        assertThat(label.getToolTipText().strip()).isEqualTo(full);
        assertThat(label.getAccessibleContext().getAccessibleDescription()).isEqualTo(full);
    }

    @Test
    @DisplayName("a short path is shown whole")
    void shortPathWhole() {
        JLabel label = ProjectExplorerTopComponent.headerPath(new File("/Users/david/NMOX"));
        assertThat(label.getText().strip()).isEqualTo(new File("/Users/david/NMOX").getAbsolutePath());
    }
}

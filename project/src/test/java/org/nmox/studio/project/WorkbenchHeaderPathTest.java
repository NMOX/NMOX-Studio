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
    @DisplayName("a deep path is shortened with its ends kept, and the whole path stays on the tooltip")
    void deepPathKeepsItsEndsAndItsWidth() {
        String deep = "/private/tmp/claude-501/-Users-david-vcs-git-github-nmox-NMOX-Studio/"
                + "791bf260-044d-4624-a2fc-7dcbfe17cd1f/scratchpad/cli/notes";
        JLabel label = ProjectExplorerTopComponent.headerPath(new File(deep));
        String shown = label.getText().strip();
        assertThat(shown.length()).isLessThanOrEqualTo(ProjectExplorerTopComponent.HEADER_PATH_MAX);
        assertThat(shown).startsWith("/private/tmp").endsWith("cli/notes").contains("…");
        assertThat(label.getToolTipText().strip()).isEqualTo(deep);
        assertThat(label.getAccessibleContext().getAccessibleDescription()).isEqualTo(deep);

        JLabel shallow = ProjectExplorerTopComponent.headerPath(new File("/Users/david/NMOX"));
        assertThat(label.getPreferredSize().width)
                .as("a deep path is no wider than a budget's worth of text")
                .isLessThanOrEqualTo(shallow.getPreferredSize().width * 4);
    }

    @Test
    @DisplayName("a short path is shown whole")
    void shortPathWhole() {
        JLabel label = ProjectExplorerTopComponent.headerPath(new File("/Users/david/NMOX"));
        assertThat(label.getText().strip()).isEqualTo("/Users/david/NMOX");
    }
}

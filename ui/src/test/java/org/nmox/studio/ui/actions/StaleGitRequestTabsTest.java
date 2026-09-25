package org.nmox.studio.ui.actions;

import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaleGitRequestTabsTest {

    private static final File MSG = new File("/r/.git/COMMIT_EDITMSG");

    @Test
    @DisplayName("a restored, unchanged tab of git's message nobody is waiting on is closed")
    void leftOverCloses() {
        assertThat(StaleGitRequestTabs.shouldClose(MSG, false, false)).isTrue();
    }

    @Test
    @DisplayName("unsaved changes, a live request, or a file of the project are never closed")
    void everythingElseStays() {
        assertThat(StaleGitRequestTabs.shouldClose(MSG, true, false)).as("unsaved changes").isFalse();
        assertThat(StaleGitRequestTabs.shouldClose(MSG, false, true)).as("a request that came with the launch").isFalse();
        assertThat(StaleGitRequestTabs.shouldClose(new File("/r/src/app.js"), false, false)).isFalse();
        assertThat(StaleGitRequestTabs.shouldClose(null, false, false)).isFalse();
    }
}

package org.nmox.studio.ui.actions;

import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The project switcher's filter discipline: the typed needle narrows the
 * recent-projects list by matching either the directory's own name or
 * anywhere in its absolute path, both case-insensitively.
 */
class SwitchProjectActionTest {

    @Test
    @DisplayName("An empty needle matches every project")
    void emptyNeedleMatchesEverything() {
        assertThat(SwitchProjectAction.matches(new File("/Users/dev/frontend"), ""))
                .isTrue();
    }

    @Test
    @DisplayName("Matches on the directory name, case-insensitively")
    void matchesOnName() {
        assertThat(SwitchProjectAction.matches(new File("/Users/dev/Frontend"), "front"))
                .isTrue();
    }

    @Test
    @DisplayName("Matches on a parent segment of the path even when the name doesn't")
    void matchesOnPathSegment() {
        assertThat(SwitchProjectAction.matches(new File("/Users/dev/work/api"), "work"))
                .isTrue();
    }

    @Test
    @DisplayName("A needle in neither name nor path does not match")
    void noMatchWhenAbsent() {
        assertThat(SwitchProjectAction.matches(new File("/Users/dev/frontend"), "backend"))
                .isFalse();
    }
}

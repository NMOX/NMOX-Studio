package org.nmox.studio.core.util;

import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitRequestFilesTest {

    @Test
    @DisplayName("a file git wrote for its editor is one of its names inside a .git folder")
    void namesInsideGit() {
        assertThat(GitRequestFiles.isRequestFile(new File("/r/.git/COMMIT_EDITMSG"))).isTrue();
        assertThat(GitRequestFiles.isRequestFile(new File("/r/.git/worktrees/w/MERGE_MSG"))).isTrue();
        assertThat(GitRequestFiles.isRequestFile(new File("/r/.git/rebase-merge/git-rebase-todo"))).isTrue();
        assertThat(GitRequestFiles.isRequestFile(new File("/r/.git/addp-hunk-edit.diff"))).isTrue();
        assertThat(GitRequestFiles.isRequestFile(new File("/r/docs/COMMIT_EDITMSG")))
                .as("the same name in the project").isFalse();
        assertThat(GitRequestFiles.isRequestFile(new File("/r/.git/config"))).isFalse();
        assertThat(GitRequestFiles.isRequestFile(null)).isFalse();
    }
}

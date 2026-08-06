package org.nmox.studio.project;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Workbench's OPEN FILES list lists FILES, once each (v1.279.0,
 * the Task Rack persona walk): opening a folder as a project leaves a
 * folder-backed editor TopComponent in the registry, so the project
 * directory itself appeared as an "open file" — and once per time it
 * had been opened, because the section rowed every TopComponent with
 * no dedup. The neighbouring RECENT FILES section already knew paths
 * repeat; this one didn't.
 */
class OpenFilesRowsTest {

    @Test
    @DisplayName("a folder is never an open-file row")
    void foldersAreNotFiles() {
        Set<String> listed = new HashSet<>();
        assertThat(ProjectExplorerTopComponent.listable(
                true, new File("/w/infra-walk"), "infra-walk", listed))
                .as("the project directory is not a file the user opened")
                .isFalse();
        assertThat(listed)
                .as("a refused row must not consume the path either")
                .isEmpty();
    }

    @Test
    @DisplayName("one row per path — a second editor on the same file adds nothing")
    void pathsDeduplicate() {
        Set<String> listed = new HashSet<>();
        assertThat(ProjectExplorerTopComponent.listable(
                false, new File("/w/app.js"), "app.js", listed)).isTrue();
        assertThat(ProjectExplorerTopComponent.listable(
                false, new File("/w/app.js"), "app.js", listed))
                .as("two TopComponents over one file is one row")
                .isFalse();
        assertThat(ProjectExplorerTopComponent.listable(
                false, new File("/w/other.js"), "other.js", listed))
                .as("a different file still lists")
                .isTrue();
    }

    @Test
    @DisplayName("a file with no disk path falls back to its title, still deduped")
    void nullFileFallsBackToTitle() {
        Set<String> listed = new HashSet<>();
        assertThat(ProjectExplorerTopComponent.listable(false, null, "Untitled", listed))
                .isTrue();
        assertThat(ProjectExplorerTopComponent.listable(false, null, "Untitled", listed))
                .as("virtual documents dedupe by title, not by a null path")
                .isFalse();
    }
}

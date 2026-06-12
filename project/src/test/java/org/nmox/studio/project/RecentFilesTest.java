package org.nmox.studio.project;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecentFilesTest {

    @Test
    @DisplayName("Newest first, refocusing moves a file up instead of duplicating it")
    void pushDedupesToFront() {
        String trail = RecentFiles.push("", "/a", 5);
        trail = RecentFiles.push(trail, "/b", 5);
        trail = RecentFiles.push(trail, "/c", 5);
        assertThat(trail).isEqualTo("/c\n/b\n/a");

        trail = RecentFiles.push(trail, "/a", 5);
        assertThat(trail).isEqualTo("/a\n/c\n/b");
    }

    @Test
    @DisplayName("The trail is capped; the oldest entry falls off")
    void pushCaps() {
        String trail = "";
        for (int i = 1; i <= 4; i++) {
            trail = RecentFiles.push(trail, "/f" + i, 3);
        }
        assertThat(trail).isEqualTo("/f4\n/f3\n/f2");
    }

    @Test
    @DisplayName("Middle-ellipsis keeps both telling ends of a deep path")
    void shortenKeepsEnds() {
        assertThat(ProjectExplorerTopComponent.shorten("short", 38)).isEqualTo("short");
        String shortened = ProjectExplorerTopComponent.shorten(
                "/Users/dev/projects/frontend/src/components/widgets", 21);
        assertThat(shortened).hasSizeLessThanOrEqualTo(21);
        assertThat(shortened).startsWith("/Users/dev").endsWith("s/widgets").contains("…");
    }
}

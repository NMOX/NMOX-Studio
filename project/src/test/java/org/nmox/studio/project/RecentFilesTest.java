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
    @DisplayName("A list drops whole items and says how many — never half a name")
    void listDropsWholeItems() {
        java.util.List<String> kinds = java.util.List.of(
                "deno", "clarity", "node", "rust", "fortran", "haskell", "purescript");
        String cut = ProjectExplorerTopComponent.shortenList(kinds, 38);
        assertThat(cut).hasSizeLessThanOrEqualTo(38).contains("deno").contains("+");
        for (String item : kinds) {
            // every name present is present WHOLE: a half-written toolchain
            // reads as one nobody has
            int at = cut.indexOf(item);
            if (at >= 0) {
                assertThat(cut.substring(at)).startsWith(item);
            }
        }
        assertThat(cut).as("a truncated name would leave a fragment before the count")
                .doesNotContain("clarit ").doesNotContain("purescrip");
        assertThat(ProjectExplorerTopComponent.shortenList(java.util.List.of("node"), 38))
                .isEqualTo("node");
    }
}

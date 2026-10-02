package org.nmox.studio.rack.service;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whatever waits for Workspace Trust hears of a grant, through any door
 * (3.5.10): the git chip's count and the language servers used to learn of
 * it only from the door they had opened themselves.
 */
class TrustGrantListenersTest {

    @BeforeEach
    void scratchStore() {
        WorkspaceTrust.clearForTest(); // never the developer's own grants (ledger 69)
        TrustNotices.forgetForTest();
    }

    @Test
    @DisplayName("a grant is told to every listener, with the folder that was trusted, after it is trusted")
    void aGrantIsHeard(@TempDir Path dir) {
        File folder = dir.toFile();
        List<String> heard = new ArrayList<>();
        Consumer<File> first = f -> heard.add("first " + f.getName() + " " + WorkspaceTrust.isTrusted(f));
        Consumer<File> second = f -> heard.add("second " + f.getName());
        WorkspaceTrust.addGrantListener(first);
        WorkspaceTrust.addGrantListener(second);
        try {
            WorkspaceTrust.trust(folder);
            assertThat(heard).containsExactly("first " + folder.getName() + " true", "second " + folder.getName());
        } finally {
            WorkspaceTrust.removeGrantListener(first);
            WorkspaceTrust.removeGrantListener(second);
        }
    }

    @Test
    @DisplayName("a listener that was removed hears nothing, and one that throws does not silence the next")
    void removedAndThrowing(@TempDir Path dir) {
        List<String> heard = new ArrayList<>();
        Consumer<File> gone = f -> heard.add("gone");
        Consumer<File> throwing = f -> {
            throw new IllegalStateException("a listener's own failure");
        };
        Consumer<File> after = f -> heard.add("after");
        WorkspaceTrust.addGrantListener(gone);
        WorkspaceTrust.addGrantListener(throwing);
        WorkspaceTrust.addGrantListener(after);
        WorkspaceTrust.removeGrantListener(gone);
        try {
            WorkspaceTrust.trust(dir.toFile());
            assertThat(heard).containsExactly("after");
            assertThat(WorkspaceTrust.isTrusted(dir.toFile())).as("and the grant itself stands").isTrue();
        } finally {
            WorkspaceTrust.removeGrantListener(throwing);
            WorkspaceTrust.removeGrantListener(after);
        }
    }

    @Test
    @DisplayName("nothing is told for no folder")
    void nullIsNotAGrant() {
        List<String> heard = new ArrayList<>();
        Consumer<File> listener = f -> heard.add("heard");
        WorkspaceTrust.addGrantListener(listener);
        try {
            WorkspaceTrust.trust(null);
            assertThat(heard).isEmpty();
        } finally {
            WorkspaceTrust.removeGrantListener(listener);
        }
    }

    @Test
    @DisplayName("one notice per folder: not again for the folder, not for a folder inside it; a sibling and a parent are their own")
    void oneNoticePerFolder(@TempDir Path dir) {
        File repo = dir.resolve("repo").toFile();
        assertThat(TrustNotices.firstFor(repo)).isTrue();
        assertThat(TrustNotices.firstFor(repo)).as("the same folder").isFalse();
        assertThat(TrustNotices.firstFor(new File(repo, "packages/app")))
                .as("a project inside an announced repository: the first click covers it").isFalse();
        assertThat(TrustNotices.firstFor(dir.resolve("repository").toFile()))
                .as("a sibling that shares the name's beginning is another folder").isTrue();
        assertThat(TrustNotices.firstFor(dir.toFile()))
                .as("a parent is not covered by a grant on its child").isTrue();
        assertThat(TrustNotices.firstFor(null)).isFalse();
    }
}

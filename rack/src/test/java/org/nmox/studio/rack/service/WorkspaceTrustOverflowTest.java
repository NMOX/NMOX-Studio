package org.nmox.studio.rack.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression guard for the trust-store overflow. Trust used to persist every
 * path joined into ONE preference value; java.util.prefs caps a value at 8 KB,
 * so a long-lived install that trusted enough projects would throw
 * "Value too long" on the next trust() and lose the ability to trust anything.
 * Storing one entry per path removes the ceiling. These tests would have thrown
 * under the old joined-value scheme.
 */
class WorkspaceTrustOverflowTest {

    @BeforeEach
    @AfterEach
    void clean() {
        WorkspaceTrust.clearForTest();
    }

    @Test
    @DisplayName("Trusting far more paths than fit in one 8 KB preference value never throws")
    void manyPathsDoNotOverflow() {
        // 200 paths of ~120 chars = ~24 KB joined — three times the old ceiling.
        List<File> dirs = new ArrayList<>();
        String deep = "/deep/nested/workspace/segment/that/pushes/the/joined/length/well/past/eight/kilobytes/on/its/own/";
        for (int i = 0; i < 200; i++) {
            dirs.add(new File(deep + "project-number-" + i));
        }

        assertThatCode(() -> dirs.forEach(WorkspaceTrust::trust)).doesNotThrowAnyException();

        // and every one of them actually stuck
        for (File dir : dirs) {
            assertThat(WorkspaceTrust.isTrusted(dir)).as(dir.getPath()).isTrue();
        }
    }

    @Test
    @DisplayName("A single path longer than the 80-char key limit is trusted (hash key, not the path)")
    void veryLongSinglePathIsTrusted() {
        File dir = new File("/" + "x".repeat(400) + "/app");
        assertThatCode(() -> WorkspaceTrust.trust(dir)).doesNotThrowAnyException();
        assertThat(WorkspaceTrust.isTrusted(dir)).isTrue();
        assertThat(WorkspaceTrust.isTrusted(new File(dir, "src"))).as("child inherits trust").isTrue();
    }

    @Test
    @DisplayName("Trusting the same path twice is idempotent (one entry, still trusted)")
    void trustIsIdempotent() {
        File dir = new File("/tmp/some/repeated/workspace");
        WorkspaceTrust.trust(dir);
        WorkspaceTrust.trust(dir);
        assertThat(WorkspaceTrust.isTrusted(dir)).isTrue();
    }
}

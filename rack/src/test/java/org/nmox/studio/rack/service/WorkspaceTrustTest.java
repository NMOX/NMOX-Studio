package org.nmox.studio.rack.service;

import java.awt.GraphicsEnvironment;
import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WorkspaceTrustTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should detect trusted directories and subdirectories correctly")
    void testWorkspaceTrustLifecycle() {
        File workspace = tempDir.toFile();
        File subDir = tempDir.resolve("subproject").toFile();

        // Initially untrusted
        assertThat(WorkspaceTrust.isTrusted(workspace)).isFalse();
        assertThat(WorkspaceTrust.isTrusted(subDir)).isFalse();

        // Trust the root workspace
        WorkspaceTrust.trust(workspace);

        // Both the workspace and its subdirectories should be trusted now
        assertThat(WorkspaceTrust.isTrusted(workspace)).isTrue();
        assertThat(WorkspaceTrust.isTrusted(subDir)).isTrue();
    }

    @Test
    @DisplayName("Headless runs cannot prompt, so trust is granted rather than silently refused")
    void headlessGrantsTrust() {
        // The regression this guards: a modal trust prompt with no user to
        // answer it (CI, tests, headless launches) returned false, blocking
        // every command launch. Headless must allow, not deny.
        assumeTrue(GraphicsEnvironment.isHeadless(), "only meaningful headless");
        File never = tempDir.resolve("never-trusted").toFile();
        assertThat(WorkspaceTrust.isTrusted(never)).isFalse();
        assertThat(WorkspaceTrust.requestTrust(never)).isTrue();
    }
}

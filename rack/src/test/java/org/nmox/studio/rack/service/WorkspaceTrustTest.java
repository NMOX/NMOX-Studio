package org.nmox.studio.rack.service;

import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

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
}

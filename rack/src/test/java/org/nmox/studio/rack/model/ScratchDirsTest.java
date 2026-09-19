package org.nmox.studio.rack.model;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The throwaway rack's scratch directory, and the healing rule that had
 * three homes with two spellings.
 */
class ScratchDirsTest {

    @Test
    @DisplayName("first call creates an EMPTY directory, later calls reuse it")
    void createsOnceAndCaches() {
        AtomicReference<File> holder = new AtomicReference<>();
        File first = ScratchDirs.cached(holder, "nmox-scratch-test");

        assertThat(first).isDirectory();
        assertThat(first.list()).as("a throwaway rack must aim at nothing").isEmpty();
        assertThat(ScratchDirs.cached(holder, "nmox-scratch-test")).isEqualTo(first);
    }

    @Test
    @DisplayName("a cached directory the OS reaped is RE-created, not handed out dead")
    void healsAVanishedDirectory() throws Exception {
        AtomicReference<File> holder = new AtomicReference<>();
        File first = ScratchDirs.cached(holder, "nmox-scratch-test");

        // macOS sweeps /var/folders under disk pressure; the process is
        // still running and the cached path is now a name and nothing else
        Files.delete(first.toPath());
        assertThat(first).doesNotExist();

        File healed = ScratchDirs.cached(holder, "nmox-scratch-test");
        assertThat(healed)
                .as("RackPresets checked only for null and would return the dead path here")
                .isDirectory()
                .isNotEqualTo(first);
    }

    @Test
    @DisplayName("a cached path that became a FILE heals too — isDirectory, not exists")
    void healsWhenTheNameIsTakenByAFile() throws Exception {
        AtomicReference<File> holder = new AtomicReference<>();
        File first = ScratchDirs.cached(holder, "nmox-scratch-test");
        Files.delete(first.toPath());
        Files.writeString(first.toPath(), "not a directory any more");

        assertThat(ScratchDirs.cached(holder, "nmox-scratch-test"))
                .isDirectory()
                .isNotEqualTo(first);
    }

    @Test
    @DisplayName("each caller keeps its own named directory — only the rule is shared")
    void holdersAreIndependent(@org.junit.jupiter.api.io.TempDir Path unused) {
        AtomicReference<File> judge = new AtomicReference<>();
        AtomicReference<File> presets = new AtomicReference<>();

        File a = ScratchDirs.cached(judge, "nmox-scratch-judge");
        File b = ScratchDirs.cached(presets, "nmox-scratch-presets");

        assertThat(a).isNotEqualTo(b);
        assertThat(a.getName()).startsWith("nmox-scratch-judge");
        assertThat(b.getName()).startsWith("nmox-scratch-presets");
    }
}

package org.nmox.studio.project;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.util.NbPreferences;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The persisted end of the recent-files trail: {@code record} and
 * {@code list} round-trip real paths through NetBeans preferences,
 * guarding against nulls and vanished files, while {@code push}'s branch
 * discipline (blank/duplicate/cap-mid-scan) is exercised directly.
 * {@link RecentFilesTest} covers the front-of-list ordering separately.
 */
class RecentFilesStoreTest {

    private static Preferences prefs() {
        return NbPreferences.forModule(RecentFiles.class);
    }

    @BeforeEach
    @AfterEach
    void clearTrail() {
        prefs().remove("recentFiles");
    }

    @Test
    @DisplayName("record then list round-trips a real file, newest first")
    void recordThenList(@TempDir Path dir) throws Exception {
        File a = Files.writeString(dir.resolve("a.txt"), "a").toFile();
        File b = Files.writeString(dir.resolve("b.txt"), "b").toFile();

        RecentFiles.record(a);
        RecentFiles.record(b);

        List<File> trail = RecentFiles.list();
        assertThat(trail).containsExactly(b, a);
    }

    @Test
    @DisplayName("record ignores null and non-existent paths")
    void recordIgnoresBadInput(@TempDir Path dir) {
        RecentFiles.record(null);
        RecentFiles.record(new File(dir.toFile(), "does-not-exist.txt"));
        assertThat(RecentFiles.list()).isEmpty();
    }

    @Test
    @DisplayName("record ignores directories — only real files join the trail")
    void recordIgnoresDirectories(@TempDir Path dir) {
        RecentFiles.record(dir.toFile());
        assertThat(RecentFiles.list()).isEmpty();
    }

    @Test
    @DisplayName("list drops entries whose files have since vanished")
    void listPrunesVanishedFiles(@TempDir Path dir) throws Exception {
        File a = Files.writeString(dir.resolve("keep.txt"), "keep").toFile();
        File gone = Files.writeString(dir.resolve("gone.txt"), "gone").toFile();
        RecentFiles.record(gone);
        RecentFiles.record(a);

        assertThat(gone.delete()).isTrue();
        assertThat(RecentFiles.list()).containsExactly(a);
    }

    @Test
    @DisplayName("list on a fresh (empty) store is empty, not a one-element blank")
    void listEmptyStore() {
        assertThat(RecentFiles.list()).isEmpty();
    }

    @Test
    @DisplayName("push skips blank existing entries in the stored value")
    void pushSkipsBlankEntries() {
        // a stored value with stray blank lines must not leak empties forward
        String result = RecentFiles.push("\n/a\n\n/b\n", "/c", 5);
        assertThat(result).isEqualTo("/c\n/a\n/b");
    }

    @Test
    @DisplayName("push stops appending once the cap is reached mid-scan")
    void pushHonoursCapMidScan() {
        String result = RecentFiles.push("/x\n/y\n/z", "/new", 2);
        assertThat(result).isEqualTo("/new\n/x");
    }

    @Test
    @DisplayName("push with cap of 1 keeps only the new head")
    void pushCapOne() {
        assertThat(RecentFiles.push("/old", "/new", 1)).isEqualTo("/new");
    }
}

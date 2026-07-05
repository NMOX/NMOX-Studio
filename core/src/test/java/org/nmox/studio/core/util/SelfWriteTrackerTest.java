package org.nmox.studio.core.util;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Self-write vs foreign-edit discrimination for a studio's workspace
 * file: the stamp the studio last wrote or loaded is "ours"; anything
 * else is a foreign change.
 */
class SelfWriteTrackerTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("the noted stamp is not foreign; any other stamp is")
    void stampDiscrimination() {
        SelfWriteTracker tracker = new SelfWriteTracker();
        tracker.noteSync(1000, 42);
        assertThat(tracker.isForeign(1000, 42)).isFalse();
        assertThat(tracker.isForeign(2000, 42)).isTrue();  // touched
        assertThat(tracker.isForeign(1000, 43)).isTrue();  // rewritten same-mtime
    }

    @Test
    @DisplayName("before any sync, every stamp is foreign except the missing-file one")
    void freshTracker() {
        SelfWriteTracker tracker = new SelfWriteTracker();
        assertThat(tracker.isForeign(1000, 42)).isTrue();
        assertThat(tracker.isForeign(-1, -1)).isFalse(); // "no file" matches "never saw one"
    }

    @Test
    @DisplayName("noteSync(File) reads the real stamp; a missing file notes -1/-1")
    void fileStamp() throws Exception {
        SelfWriteTracker tracker = new SelfWriteTracker();
        Path file = dir.resolve("w.json");
        Files.writeString(file, "{}");
        tracker.noteSync(file.toFile());
        assertThat(tracker.isForeign(file.toFile().lastModified(),
                file.toFile().length())).isFalse();

        tracker.noteSync(dir.resolve("gone.json").toFile());
        assertThat(tracker.isForeign(-1, -1)).isFalse();
        assertThat(tracker.isForeign(file.toFile().lastModified(),
                file.toFile().length())).isTrue();
    }
}

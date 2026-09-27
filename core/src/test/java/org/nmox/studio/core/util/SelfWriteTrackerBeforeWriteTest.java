package org.nmox.studio.core.util;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** A studio writes only over the bytes it last saw (3.4, question 1). */
class SelfWriteTrackerBeforeWriteTest {

    private static final String OURS = "<".repeat(7);
    private static final String THEIRS = ">".repeat(7);

    @Test
    @DisplayName("our own bytes, or nothing on disk: the write goes ahead")
    void oursOrNothing(@TempDir File dir) throws Exception {
        File f = new File(dir, ".nmoxapi.json");
        SelfWriteTracker t = new SelfWriteTracker();
        t.noteSync(f);
        assertThat(t.beforeWrite(f, 1 << 20)).as("never written").isEqualTo(SelfWriteTracker.OnDisk.OURS);
        Files.writeString(f.toPath(), "{\"a\":1}");
        t.noteSync(f);
        assertThat(t.beforeWrite(f, 1 << 20)).isEqualTo(SelfWriteTracker.OnDisk.OURS);
    }

    @Test
    @DisplayName("a pull that changed the file: CHANGED; a pull that left git's conflict: CONFLICTED")
    void pulledUnderneath(@TempDir File dir) throws Exception {
        File f = new File(dir, ".nmoxdb.json");
        Files.writeString(f.toPath(), "{\"a\":1}");
        SelfWriteTracker t = new SelfWriteTracker();
        t.noteSync(f);
        Files.writeString(f.toPath(), "{\"a\":1, \"b\":2}");
        assertThat(t.beforeWrite(f, 1 << 20)).isEqualTo(SelfWriteTracker.OnDisk.CHANGED);
        Files.writeString(f.toPath(), "{\n" + OURS + " HEAD\n\"a\":1\n" + "=".repeat(7) + "\n\"a\":2\n" + THEIRS + " bob\n}\n");
        assertThat(t.beforeWrite(f, 1 << 20)).isEqualTo(SelfWriteTracker.OnDisk.CONFLICTED);
        assertThat(t.beforeWrite(f, 4)).as("too big to read is still not ours").isEqualTo(SelfWriteTracker.OnDisk.CHANGED);
    }

    @Test
    @DisplayName("a file that appeared where there was none is somebody's")
    void appeared(@TempDir File dir) throws Exception {
        File f = new File(dir, ".nmoxrack.json");
        SelfWriteTracker t = new SelfWriteTracker();
        t.noteSync(f);
        Files.writeString(f.toPath(), "{}");
        assertThat(t.beforeWrite(f, 1 << 20)).isEqualTo(SelfWriteTracker.OnDisk.CHANGED);
    }
}

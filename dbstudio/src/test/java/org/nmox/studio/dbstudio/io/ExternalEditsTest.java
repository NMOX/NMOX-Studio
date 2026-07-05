package org.nmox.studio.dbstudio.io;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.dbstudio.io.ExternalEdits.Stamp;
import org.nmox.studio.dbstudio.io.ExternalEdits.Verdict;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Self-write vs foreign-write discrimination for .nmoxdb.json: own
 * versions stay quiet, a foreign version reloads exactly once (the
 * bounded-reaction law), and a busy studio defers WITHOUT consuming —
 * the post-busy re-check still fires.
 */
class ExternalEditsTest {

    @Test
    @DisplayName("A missing file is never a reload — deletion must not clobber memory")
    void missingFileIsNone() {
        ExternalEdits edits = new ExternalEdits();
        edits.recordOwn(new Stamp(1000, 50));

        assertThat(edits.check(null, false)).isEqualTo(Verdict.NONE);
        assertThat(edits.check(null, true)).isEqualTo(Verdict.NONE);
    }

    @Test
    @DisplayName("The version we wrote ourselves stays quiet")
    void ownWriteIsNone() {
        ExternalEdits edits = new ExternalEdits();
        edits.recordOwn(new Stamp(1000, 50));

        assertThat(edits.check(new Stamp(1000, 50), false)).isEqualTo(Verdict.NONE);
    }

    @Test
    @DisplayName("A foreign version reloads once and only once (bounded reaction)")
    void foreignVersionReloadsOnce() {
        ExternalEdits edits = new ExternalEdits();
        edits.recordOwn(new Stamp(1000, 50));
        Stamp foreign = new Stamp(2000, 61);

        assertThat(edits.check(foreign, false)).isEqualTo(Verdict.RELOAD);
        assertThat(edits.check(foreign, false))
                .as("the same version never triggers twice — no 2-second drumbeat")
                .isEqualTo(Verdict.NONE);
    }

    @Test
    @DisplayName("Same mtime but different size is still a foreign version")
    void sizeAloneDiscriminates() {
        ExternalEdits edits = new ExternalEdits();
        edits.recordOwn(new Stamp(1000, 50));

        assertThat(edits.check(new Stamp(1000, 51), false)).isEqualTo(Verdict.RELOAD);
    }

    @Test
    @DisplayName("Busy defers without consuming — the re-check when free still reloads")
    void busyDefersWithoutConsuming() {
        ExternalEdits edits = new ExternalEdits();
        edits.recordOwn(new Stamp(1000, 50));
        Stamp foreign = new Stamp(2000, 61);

        assertThat(edits.check(foreign, true)).isEqualTo(Verdict.DEFER);
        assertThat(edits.check(foreign, true))
                .as("still busy, still waiting")
                .isEqualTo(Verdict.DEFER);
        assertThat(edits.check(foreign, false))
                .as("free again: the deferred version finally reloads")
                .isEqualTo(Verdict.RELOAD);
        assertThat(edits.check(foreign, false)).isEqualTo(Verdict.NONE);
    }

    @Test
    @DisplayName("Each NEW foreign version reacts again")
    void newForeignVersionReactsAgain() {
        ExternalEdits edits = new ExternalEdits();
        edits.recordOwn(new Stamp(1000, 50));

        assertThat(edits.check(new Stamp(2000, 61), false)).isEqualTo(Verdict.RELOAD);
        assertThat(edits.check(new Stamp(3000, 42), false)).isEqualTo(Verdict.RELOAD);
    }

    @Test
    @DisplayName("A save after a reload makes the new version ours again")
    void recordOwnResets() {
        ExternalEdits edits = new ExternalEdits();
        edits.recordOwn(new Stamp(1000, 50));
        assertThat(edits.check(new Stamp(2000, 61), false)).isEqualTo(Verdict.RELOAD);

        edits.recordOwn(new Stamp(4000, 70)); // our own subsequent save
        assertThat(edits.check(new Stamp(4000, 70), false)).isEqualTo(Verdict.NONE);
    }

    @Test
    @DisplayName("With no file ever seen (known null), a created file is foreign")
    void createdFileAfterNothingIsForeign() {
        ExternalEdits edits = new ExternalEdits();
        edits.recordOwn(null); // project had no .nmoxdb.json at load time

        assertThat(edits.check(new Stamp(2000, 61), false)).isEqualTo(Verdict.RELOAD);
    }

    @Test
    @DisplayName("Stamp.of reads mtime+size from a real file and null from a missing one")
    void stampOf(@TempDir Path dir) throws Exception {
        File file = dir.resolve("ws.json").toFile();
        Files.writeString(file.toPath(), "{\"version\":1}", StandardCharsets.UTF_8);

        Stamp stamp = Stamp.of(file);
        assertThat(stamp).isNotNull();
        assertThat(stamp.size()).isEqualTo(file.length());
        assertThat(stamp.mtime()).isEqualTo(file.lastModified());

        assertThat(Stamp.of(dir.resolve("absent.json").toFile())).isNull();
        assertThat(Stamp.of(null)).isNull();
        assertThat(Stamp.of(dir.toFile())).as("a directory is not a workspace file").isNull();
    }
}

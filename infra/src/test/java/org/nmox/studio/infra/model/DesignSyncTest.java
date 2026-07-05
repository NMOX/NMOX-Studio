package org.nmox.studio.infra.model;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.infra.model.DesignSync.Stamp;
import org.nmox.studio.infra.model.DesignSync.Verdict;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The .nmoxinfra.json conflict matrix: own writes stay quiet, a
 * foreign version reloads a clean canvas and asks a dirty one — each
 * version exactly once (the bounded-reaction law).
 */
class DesignSyncTest {

    // ---- the conflict matrix ---------------------------------------------

    @Test
    @DisplayName("Our own version is NONE, clean or dirty")
    void ownVersionIsNone() {
        DesignSync sync = new DesignSync();
        sync.recordOwn(new Stamp(1000, 50));

        assertThat(sync.check(new Stamp(1000, 50), false)).isEqualTo(Verdict.NONE);
        assertThat(sync.check(new Stamp(1000, 50), true)).isEqualTo(Verdict.NONE);
    }

    @Test
    @DisplayName("A foreign version on a clean canvas reloads")
    void foreignCleanReloads() {
        DesignSync sync = new DesignSync();
        sync.recordOwn(new Stamp(1000, 50));

        assertThat(sync.check(new Stamp(2000, 61), false)).isEqualTo(Verdict.RELOAD);
    }

    @Test
    @DisplayName("A foreign version against unsaved canvas edits is a CONFLICT — never a silent clobber")
    void foreignDirtyConflicts() {
        DesignSync sync = new DesignSync();
        sync.recordOwn(new Stamp(1000, 50));

        assertThat(sync.check(new Stamp(2000, 61), true)).isEqualTo(Verdict.CONFLICT);
    }

    @Test
    @DisplayName("A missing file is NONE, clean or dirty — deletion never clobbers the canvas")
    void missingFileIsNone() {
        DesignSync sync = new DesignSync();
        sync.recordOwn(new Stamp(1000, 50));

        assertThat(sync.check(null, false)).isEqualTo(Verdict.NONE);
        assertThat(sync.check(null, true)).isEqualTo(Verdict.NONE);
    }

    // ---- bounded reactions (the storm law) --------------------------------

    @Test
    @DisplayName("One reload per foreign version — the 2s timer never drumbeats")
    void reloadFiresOncePerVersion() {
        DesignSync sync = new DesignSync();
        sync.recordOwn(new Stamp(1000, 50));
        Stamp foreign = new Stamp(2000, 61);

        assertThat(sync.check(foreign, false)).isEqualTo(Verdict.RELOAD);
        assertThat(sync.check(foreign, false)).isEqualTo(Verdict.NONE);
        assertThat(sync.check(foreign, true)).isEqualTo(Verdict.NONE);
    }

    @Test
    @DisplayName("One conflict balloon per foreign version, even while still dirty")
    void conflictFiresOncePerVersion() {
        DesignSync sync = new DesignSync();
        sync.recordOwn(new Stamp(1000, 50));
        Stamp foreign = new Stamp(2000, 61);

        assertThat(sync.check(foreign, true)).isEqualTo(Verdict.CONFLICT);
        assertThat(sync.check(foreign, true))
                .as("the balloon shows once; ignoring it is an answer")
                .isEqualTo(Verdict.NONE);
        assertThat(sync.check(foreign, false)).isEqualTo(Verdict.NONE);
    }

    @Test
    @DisplayName("Each NEW foreign version reacts again")
    void newVersionReactsAgain() {
        DesignSync sync = new DesignSync();
        sync.recordOwn(new Stamp(1000, 50));

        assertThat(sync.check(new Stamp(2000, 61), false)).isEqualTo(Verdict.RELOAD);
        assertThat(sync.check(new Stamp(3000, 42), true)).isEqualTo(Verdict.CONFLICT);
        assertThat(sync.check(new Stamp(4000, 43), false)).isEqualTo(Verdict.RELOAD);
    }

    // ---- discrimination edges ----------------------------------------------

    @Test
    @DisplayName("Same mtime but different size is still a foreign version")
    void sizeAloneDiscriminates() {
        DesignSync sync = new DesignSync();
        sync.recordOwn(new Stamp(1000, 50));

        assertThat(sync.check(new Stamp(1000, 51), false)).isEqualTo(Verdict.RELOAD);
    }

    @Test
    @DisplayName("With no file at load time, a created file is foreign")
    void createdFileIsForeign() {
        DesignSync sync = new DesignSync();
        sync.recordOwn(null); // the project had no .nmoxinfra.json

        assertThat(sync.check(new Stamp(2000, 61), false)).isEqualTo(Verdict.RELOAD);
    }

    @Test
    @DisplayName("A save after the reaction makes the new version ours again")
    void recordOwnResets() {
        DesignSync sync = new DesignSync();
        sync.recordOwn(new Stamp(1000, 50));
        assertThat(sync.check(new Stamp(2000, 61), false)).isEqualTo(Verdict.RELOAD);

        sync.recordOwn(new Stamp(4000, 70)); // the debounced save fired
        assertThat(sync.check(new Stamp(4000, 70), false)).isEqualTo(Verdict.NONE);
        assertThat(sync.check(new Stamp(4000, 70), true)).isEqualTo(Verdict.NONE);
    }

    @Test
    @DisplayName("Stamp.of reads mtime+size from a real file and null from a missing one")
    void stampOf(@TempDir Path dir) throws Exception {
        File file = dir.resolve("design.json").toFile();
        Files.writeString(file.toPath(), "{\"nodes\":[]}", StandardCharsets.UTF_8);

        Stamp stamp = Stamp.of(file);
        assertThat(stamp).isNotNull();
        assertThat(stamp.size()).isEqualTo(file.length());
        assertThat(stamp.mtime()).isEqualTo(file.lastModified());

        assertThat(Stamp.of(dir.resolve("absent.json").toFile())).isNull();
        assertThat(Stamp.of(null)).isNull();
        assertThat(Stamp.of(dir.toFile())).as("a directory is not a design file").isNull();
    }
}

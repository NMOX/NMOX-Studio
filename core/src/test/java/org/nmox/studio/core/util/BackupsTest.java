package org.nmox.studio.core.util;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** A rescue never overwrites an earlier rescue (3.4). */
class BackupsTest {

    @Test
    @DisplayName("the first rescue keeps the name every message speaks: <name>.bak")
    void firstIsBak(@TempDir Path dir) {
        Path f = dir.resolve(".nmoxtasks.json");
        assertThat(Backups.freeSibling(f)).isEqualTo(dir.resolve(".nmoxtasks.json.bak"));
    }

    @Test
    @DisplayName("a second rescue takes .bak.1 and a third .bak.2 — the earlier bytes survive")
    void laterRescuesAreNumbered(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(".nmoxrack.json");
        Files.writeString(dir.resolve(".nmoxrack.json.bak"), "first");
        assertThat(Backups.freeSibling(f)).isEqualTo(dir.resolve(".nmoxrack.json.bak.1"));
        Files.writeString(dir.resolve(".nmoxrack.json.bak.1"), "second");
        assertThat(Backups.freeSibling(f)).isEqualTo(dir.resolve(".nmoxrack.json.bak.2"));
        assertThat(Files.readString(dir.resolve(".nmoxrack.json.bak"))).isEqualTo("first");
    }

    @Test
    @DisplayName("a dangling link at .bak counts as taken — its name is never reused")
    void danglingLinkIsTaken(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(".nmoxblocks.json");
        try {
            Files.createSymbolicLink(dir.resolve(".nmoxblocks.json.bak"), dir.resolve("nowhere"));
        } catch (UnsupportedOperationException | java.io.IOException noLinks) {
            return; // a filesystem without links cannot hold this case
        }
        assertThat(Backups.freeSibling(f)).isEqualTo(dir.resolve(".nmoxblocks.json.bak.1"));
    }
}

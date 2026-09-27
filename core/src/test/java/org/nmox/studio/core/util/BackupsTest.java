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
    @DisplayName("a second rescue takes .2.bak and a third .3.bak — the earlier bytes survive")
    void laterRescuesAreNumbered(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(".nmoxrack.json");
        Files.writeString(dir.resolve(".nmoxrack.json.bak"), "first");
        assertThat(Backups.freeSibling(f)).isEqualTo(dir.resolve(".nmoxrack.json.2.bak"));
        Files.writeString(dir.resolve(".nmoxrack.json.2.bak"), "second");
        assertThat(Backups.freeSibling(f)).isEqualTo(dir.resolve(".nmoxrack.json.3.bak"));
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
        assertThat(Backups.freeSibling(f)).isEqualTo(dir.resolve(".nmoxblocks.json.2.bak"));
    }

    @Test
    @DisplayName("copyAside: a second and third rescue take .2.bak and .3.bak, each keeping its own bytes")
    void copyAsideNeverOverwrites(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(".nmoxdb.json");
        Files.writeString(f, "first");
        java.io.File one = Backups.copyAside(f.toFile());
        Files.writeString(f, "second");
        java.io.File two = Backups.copyAside(f.toFile());
        Files.writeString(f, "third");
        java.io.File three = Backups.copyAside(f.toFile());
        assertThat(one.getName()).isEqualTo(".nmoxdb.json.bak");
        assertThat(two.getName()).isEqualTo(".nmoxdb.json.2.bak");
        assertThat(three.getName()).isEqualTo(".nmoxdb.json.3.bak");
        assertThat(Files.readString(one.toPath())).as("the first rescue keeps the bytes it rescued").isEqualTo("first");
        assertThat(Files.readString(two.toPath())).isEqualTo("second");
        assertThat(Files.readString(three.toPath())).isEqualTo("third");
    }

    @Test
    @DisplayName("the same broken bytes, rescued again (a re-aim, a search), reuse their copy: no pile of .N.bak")
    void sameBytesReuseTheirCopy(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(".nmoxinfra.json");
        Files.writeString(f, "{ broken");
        java.io.File first = Backups.copyAside(f.toFile());
        assertThat(Backups.copyAside(f.toFile())).isEqualTo(first);
        assertThat(Backups.keep(f, Files.readAllBytes(f)).toFile()).isEqualTo(first);
        assertThat(dir.resolve(".nmoxinfra.json.2.bak")).doesNotExist();
    }

    @Test
    @DisplayName("a gap is reused: the first FREE name, not the next number")
    void firstFreeName(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(".nmoxinfra.json");
        Files.writeString(dir.resolve(".nmoxinfra.json.bak"), "old");
        Files.writeString(dir.resolve(".nmoxinfra.json.3.bak"), "older");
        assertThat(Backups.freeSibling(f)).isEqualTo(dir.resolve(".nmoxinfra.json.2.bak"));
    }

    @Test
    @DisplayName("a directory squatting on .bak is never touched; the copy takes the next name")
    void squatterIsSkipped(@TempDir Path dir) throws Exception {
        Path f = dir.resolve(".nmoxweb3.json");
        Files.writeString(f, "{ x");
        Files.createDirectory(dir.resolve(".nmoxweb3.json.bak"));
        assertThat(Backups.copyAside(f.toFile()).getName()).isEqualTo(".nmoxweb3.json.2.bak");
    }
}

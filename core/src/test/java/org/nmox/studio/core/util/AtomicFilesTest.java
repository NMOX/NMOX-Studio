package org.nmox.studio.core.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class AtomicFilesTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Content lands exactly, UTF-8, on a fresh file")
    void writesContent() throws Exception {
        Path target = dir.resolve("out.json");

        AtomicFiles.writeString(target, "{\"key\": \"värde\"}\n");

        assertThat(Files.readString(target, StandardCharsets.UTF_8))
                .isEqualTo("{\"key\": \"värde\"}\n");
    }

    @Test
    @DisplayName("Overwriting an existing file replaces the old content")
    void overwritesExisting() throws Exception {
        Path target = dir.resolve("out.json");
        Files.writeString(target, "old and much longer than the replacement");

        AtomicFiles.writeString(target, "new");

        assertThat(Files.readString(target)).isEqualTo("new");
    }

    @Test
    @DisplayName("No *.tmp residue remains in the directory after a write")
    void leavesNoTempResidue() throws Exception {
        Path target = dir.resolve("out.json");

        AtomicFiles.writeString(target, "first");
        AtomicFiles.writeString(target, "second");

        try (Stream<Path> files = Files.list(dir)) {
            List<Path> leftovers = files
                    .filter(p -> !p.getFileName().toString().equals("out.json"))
                    .toList();
            assertThat(leftovers).isEmpty();
        }
    }

    private Path temp(String name, long ageMillis) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, "half a save");
        Files.setLastModifiedTime(p, java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() - ageMillis));
        return p;
    }

    @Test
    @DisplayName("A write sweeps the stale temps a killed save of the SAME file left, and nothing else (3.4)")
    void sweepsStaleTempsOfTheSameTarget() throws Exception {
        Path target = dir.resolve(".nmoxrack.json");
        Path staleNew = temp(".nmox-save-.nmoxrack.json.4815162342.tmp", 120_000);
        Path staleOld = temp(".nmoxrack.json8230947123.tmp", 3_600_000); // the pre-3.4 spelling
        Path fresh = temp(".nmox-save-.nmoxrack.json.99.tmp", 1_000);     // another instance saving now
        Path otherTarget = temp(".nmox-save-.nmoxapi.json.12.tmp", 120_000);
        Path userTemp = temp("notes.tmp", 120_000);
        Path notDigits = temp(".nmox-save-.nmoxrack.json.abc.tmp", 120_000);
        Path dirLookalike = dir.resolve(".nmox-save-.nmoxrack.json.7.tmp.d");
        Files.createDirectories(dirLookalike);

        AtomicFiles.writeString(target, "{}");

        assertThat(staleNew).as("a stale temp of this file").doesNotExist();
        assertThat(staleOld).as("a stale temp in the old spelling").doesNotExist();
        assertThat(fresh).as("under a minute old: maybe a save in progress").exists();
        assertThat(otherTarget).as("another file's temp is that file's to sweep").exists();
        assertThat(userTemp).as("not ours").exists();
        assertThat(notDigits).as("not a name this class makes").exists();
        assertThat(dirLookalike).exists();
        assertThat(Files.readString(target)).isEqualTo("{}");
    }

    @Test
    @DisplayName("Temps are hidden and carry the IDE's own mark, so REFLEX and the tree ignore them")
    void tempNamesAreOurs() {
        assertThat(AtomicFiles.isTempOf(".nmox-save-package.json.123.tmp", "package.json")).isTrue();
        assertThat(IdeWorkspaceFiles.isOwn(".nmox-save-package.json.123.tmp")).isTrue();
        assertThat(IdeWorkspaceFiles.isOwn(".nmoxrack.json8230947123.tmp"))
                .as("the pre-3.4 leftover of a workspace save").isTrue();
        assertThat(IdeWorkspaceFiles.isOwn(".nmoxrack.jsonx.tmp")).isFalse();
    }
}

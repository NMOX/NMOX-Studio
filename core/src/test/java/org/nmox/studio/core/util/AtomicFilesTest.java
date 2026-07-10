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
}

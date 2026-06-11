package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileOpsTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Should create, rename and delete files")
    void shouldCrudFiles() throws Exception {
        File created = FileOps.createFile(dir.toFile(), "app.js");
        assertThat(created).exists();

        File renamed = FileOps.rename(created, "index.js");
        assertThat(renamed).exists();
        assertThat(created).doesNotExist();

        FileOps.delete(renamed);
        assertThat(renamed).doesNotExist();
    }

    @Test
    @DisplayName("Should create and recursively delete directories")
    void shouldCrudDirectories() throws Exception {
        File sub = FileOps.createDirectory(dir.toFile(), "src");
        Files.writeString(sub.toPath().resolve("a.js"), "1");
        File nested = FileOps.createDirectory(sub, "deep");
        Files.writeString(nested.toPath().resolve("b.js"), "2");

        FileOps.delete(sub);

        assertThat(sub).doesNotExist();
    }

    @Test
    @DisplayName("Should reject invalid and clobbering names")
    void shouldRejectBadNames() throws Exception {
        FileOps.createFile(dir.toFile(), "taken.js");

        assertThrows(IOException.class, () -> FileOps.createFile(dir.toFile(), "taken.js"));
        assertThrows(IOException.class, () -> FileOps.createFile(dir.toFile(), "a/b.js"));
        assertThrows(IOException.class, () -> FileOps.createFile(dir.toFile(), ".."));
        assertThrows(IOException.class, () -> FileOps.createFile(dir.toFile(), "  "));
    }
}

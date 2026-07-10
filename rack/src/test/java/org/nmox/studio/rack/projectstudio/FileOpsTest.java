package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

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

    // ---- the platform path: the DataObject (and so any open editor) follows ----

    @Test
    @DisplayName("Rename goes through the DataObject, which follows to the new name")
    void renameUpdatesDataObject() throws Exception {
        File created = FileOps.createFile(dir.toFile(), "app.js");
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(created));
        assertThat(fo).as("masterfs must see the file in tests").isNotNull();
        DataObject dob = DataObject.find(fo);

        FileOps.rename(created, "index.js");

        assertThat(dob.isValid()).isTrue();
        assertThat(dob.getPrimaryFile().getNameExt()).isEqualTo("index.js");
    }

    @Test
    @DisplayName("Rename may change the extension (below the DataObject)")
    void renameAcrossExtensions() throws Exception {
        File created = FileOps.createFile(dir.toFile(), "app.js");
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(created));
        assertThat(fo).isNotNull();

        File renamed = FileOps.rename(created, "app.ts");

        assertThat(renamed).exists();
        assertThat(created).doesNotExist();
        // masterfs renames in place: the same FileObject carries the new name
        assertThat(fo.getNameExt()).isEqualTo("app.ts");
    }

    @Test
    @DisplayName("Delete invalidates the FileObject so stale buffers cannot survive")
    void deleteInvalidatesFileObject() throws Exception {
        File created = FileOps.createFile(dir.toFile(), "gone.js");
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(created));
        assertThat(fo).isNotNull();

        FileOps.delete(created);

        // both delete paths (Trash + refresh, DataObject.delete) end here
        assertThat(fo.isValid()).isFalse();
        assertThat(created).doesNotExist();
    }

    @Test
    @DisplayName("Creation registers with masterfs immediately (no stale tree)")
    void createIsVisibleToMasterfs() throws Exception {
        File created = FileOps.createFile(dir.toFile(), "seen.js");
        File folder = FileOps.createDirectory(dir.toFile(), "seenDir");

        assertThat(FileUtil.toFileObject(FileUtil.normalizeFile(created))).isNotNull();
        FileObject dirFo = FileUtil.toFileObject(FileUtil.normalizeFile(folder));
        assertThat(dirFo).isNotNull();
        assertThat(dirFo.isFolder()).isTrue();
    }
}

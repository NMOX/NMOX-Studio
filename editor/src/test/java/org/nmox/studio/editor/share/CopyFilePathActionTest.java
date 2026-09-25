package org.nmox.studio.editor.share;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JEditorPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edit ▸ Copy Path and Copy Relative Path: which file, which root, and
 * that no file on screen is said rather than copied as nothing.
 */
class CopyFilePathActionTest {

    private Consumer<String> savedClip;
    private Consumer<String> savedStatus;
    private final List<String> copied = new ArrayList<>();
    private final List<String> said = new ArrayList<>();

    @BeforeEach
    void seams() {
        savedClip = CopyFilePathAction.clipboard;
        savedStatus = CopyFilePathAction.status;
        CopyFilePathAction.clipboard = copied::add;
        CopyFilePathAction.status = said::add;
    }

    @AfterEach
    void restore() {
        CopyFilePathAction.clipboard = savedClip;
        CopyFilePathAction.status = savedStatus;
    }

    @Test
    @DisplayName("an editor's file is the DataObject its document describes; a scratch buffer has none")
    void fileOfAnEditor(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("app.js");
        Files.writeString(f, "x");
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(f.toFile()));
        JEditorPane pane = new JEditorPane();
        pane.getDocument().putProperty(javax.swing.text.Document.StreamDescriptionProperty, DataObject.find(fo));
        assertThat(CopyFilePathAction.fileOf(pane)).isEqualTo(FileUtil.normalizeFile(f.toFile()));
        assertThat(CopyFilePathAction.fileOf(new JEditorPane())).isNull();
        assertThat(CopyFilePathAction.fileOf(null)).isNull();
    }

    @Test
    @DisplayName("with no editor holding a file, both say so and copy nothing")
    void noEditorSpeaks() {
        new CopyFilePathAction.Absolute().actionPerformed(null);
        new CopyFilePathAction.Relative().actionPerformed(null);
        assertThat(copied).isEmpty();
        assertThat(said).containsExactly("Copy Path: no editor holds a file on disk",
                "Copy Path: no editor holds a file on disk");
    }

    @Test
    @DisplayName("Copy Path is absolute; a file in no project copies its absolute path as the relative one too")
    void paths(@TempDir Path dir) throws Exception {
        File f = dir.resolve("loose.txt").toFile();
        Files.writeString(f.toPath(), "x");
        assertThat(new CopyFilePathAction.Absolute().pathOf(f)).isEqualTo(f.getAbsolutePath());
        assertThat(new CopyFilePathAction.Relative().pathOf(f)).isEqualTo(f.getAbsolutePath());
    }

    @Test
    @DisplayName("the relative root is the aimed project when the file is inside it, and nothing when it is not")
    void relativeRoot(@TempDir Path dir) throws Exception {
        File aimed = dir.resolve("shop").toFile();
        File inside = dir.resolve("shop/src/app.js").toFile();
        Files.createDirectories(inside.toPath().getParent());
        Files.writeString(inside.toPath(), "x");
        assertThat(CopyFilePathAction.rootFor(inside, aimed)).isEqualTo(aimed);
        File outside = dir.resolve("shopping/app.js").toFile();
        Files.createDirectories(outside.toPath().getParent());
        Files.writeString(outside.toPath(), "x");
        assertThat(CopyFilePathAction.rootFor(outside, aimed))
                .as("a sibling sharing the aimed project's prefix is not inside it").isNull();
        assertThat(org.nmox.studio.core.util.PathLabel.relative(CopyFilePathAction.rootFor(inside, aimed), inside))
                .isEqualTo("src" + File.separator + "app.js");
    }
}

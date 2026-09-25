package org.nmox.studio.core.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JEditorPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The file an editor holds, not its group's primary: the properties module
 * opens {@code Bundle_de.properties} with a document whose stream is the
 * whole {@code Bundle} group and whose title names the German file (3.2
 * fourth review).
 */
class EditedFileTest {

    private static FileObject file(Path dir, String name) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, "k=v\n");
        return FileUtil.toFileObject(FileUtil.normalizeFile(p.toFile()));
    }

    @Test
    @DisplayName("a group of files names the one its document's title names; the primary only when it is the one")
    void groupMemberByTitle(@TempDir Path dir) throws Exception {
        FileObject base = file(dir, "Bundle.properties");
        FileObject de = file(dir, "Bundle_de.properties");
        List<FileObject> group = List.of(base, de);
        assertThat(EditedFile.pick(de.toString(), base, group)).isEqualTo(de);
        assertThat(EditedFile.pick(base.toString(), base, group)).isEqualTo(base);
        assertThat(EditedFile.pick(FileUtil.getFileDisplayName(de), base, group)).isEqualTo(de);
        assertThat(EditedFile.pick(de.getPath(), base, group)).isEqualTo(de);
    }

    @Test
    @DisplayName("a group whose document names none of its files answers nothing, never the primary")
    void groupUnnamedIsNothing(@TempDir Path dir) throws Exception {
        FileObject base = file(dir, "Bundle.properties");
        FileObject de = file(dir, "Bundle_de.properties");
        assertThat(EditedFile.pick(null, base, List.of(base, de))).isNull();
        assertThat(EditedFile.pick("something else", base, List.of(base, de))).isNull();
    }

    @Test
    @DisplayName("one file is the file edited, whatever the title says")
    void singleFile(@TempDir Path dir) throws Exception {
        FileObject app = file(dir, "app.js");
        assertThat(EditedFile.pick(null, app, List.of(app))).isEqualTo(app);
        JEditorPane pane = new JEditorPane();
        pane.getDocument().putProperty(javax.swing.text.Document.StreamDescriptionProperty, DataObject.find(app));
        assertThat(EditedFile.of(pane.getDocument())).isEqualTo(app);
        assertThat(EditedFile.of(new JEditorPane().getDocument())).isNull();
        JEditorPane named = new JEditorPane();
        named.getDocument().putProperty(javax.swing.text.Document.StreamDescriptionProperty, app);
        assertThat(EditedFile.of(named.getDocument())).as("a document that names its file directly").isEqualTo(app);
        assertThat(EditedFile.of(null)).isNull();
    }

    @Test
    @DisplayName("a tab's file: its own document decides, a stranger's document does not, no document means the single file")
    void tabRule(@TempDir Path dir) throws Exception {
        FileObject app = file(dir, "app.js");
        FileObject other = file(dir, "other.js");
        DataObject appDob = DataObject.find(app);
        JEditorPane mine = new JEditorPane();
        mine.getDocument().putProperty(javax.swing.text.Document.StreamDescriptionProperty, appDob);
        JEditorPane stranger = new JEditorPane();
        stranger.getDocument().putProperty(javax.swing.text.Document.StreamDescriptionProperty,
                DataObject.find(other));
        assertThat(EditedFile.of(appDob, mine.getDocument())).isEqualTo(app);
        assertThat(EditedFile.of(appDob, stranger.getDocument())).as("another tab's document").isEqualTo(app);
        assertThat(EditedFile.of(appDob, null)).as("an editor not built yet").isEqualTo(app);
        assertThat(EditedFile.of((DataObject) null, null)).isNull();
    }
}

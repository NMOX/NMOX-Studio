package org.nmox.studio.editor.a11y;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JEditorPane;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An editor pane is named after its file, so a screen reader hears "Editor
 * for COMMIT_EDITMSG" where the platform said "Editor for null" (3.2.0).
 */
class EditorAccessibleNamesTest {

    @TempDir
    Path tmp;

    private JEditorPane paneOver(Object streamDescription) {
        JEditorPane pane = new JEditorPane();
        Document doc = new PlainDocument();
        doc.putProperty(Document.StreamDescriptionProperty, streamDescription);
        pane.setDocument(doc);
        return pane;
    }

    @Test
    @DisplayName("a pane over a file's DataObject is named after the file")
    void namedAfterTheFile() throws Exception {
        Path f = Files.writeString(tmp.resolve("COMMIT_EDITMSG"), "msg\n");
        DataObject dob = DataObject.find(FileUtil.toFileObject(f.toFile()));
        JEditorPane pane = paneOver(dob);
        EditorAccessibleNames.name(pane);
        assertThat(pane.getName()).isEqualTo("COMMIT_EDITMSG");
    }

    @Test
    @DisplayName("a pane over a bare FileObject is named after it too")
    void namedAfterAFileObject() throws Exception {
        Path f = Files.writeString(tmp.resolve("app.js"), "x");
        JEditorPane pane = paneOver(FileUtil.toFileObject(f.toFile()));
        EditorAccessibleNames.name(pane);
        assertThat(pane.getName()).isEqualTo("app.js");
    }

    @Test
    @DisplayName("after a rename the pane is named after the file it now shows, and a screen reader hears it")
    void followsARename() throws Exception {
        Path f = Files.writeString(tmp.resolve("old.js"), "x");
        DataObject dob = DataObject.find(FileUtil.toFileObject(f.toFile()));
        JEditorPane pane = paneOver(dob);
        EditorAccessibleNames.name(pane);
        dob.rename("new");
        EditorAccessibleNames.name(pane);
        assertThat(pane.getName()).isEqualTo("new.js");
    }

    @Test
    @DisplayName("a name someone else set is kept, and a document with no file stays unnamed")
    void leavesOthersAlone() throws Exception {
        Path f = Files.writeString(tmp.resolve("a.txt"), "x");
        JEditorPane named = paneOver(FileUtil.toFileObject(f.toFile()));
        named.setName("search field");
        EditorAccessibleNames.name(named);
        assertThat(named.getName()).isEqualTo("search field");
        JEditorPane noFile = paneOver(null);
        EditorAccessibleNames.name(noFile);
        assertThat(noFile.getName()).isNull();
    }

    @Test
    @DisplayName("the namer is installed at startup, on the editor registry")
    void installedAtStart() throws Exception {
        String layer = new String(EditorAccessibleNames.class.getClassLoader()
                .getResourceAsStream("META-INF/namedservices/Modules/Start/java.lang.Runnable").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(layer).contains(EditorAccessibleNames.class.getName());
    }
}

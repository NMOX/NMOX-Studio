package org.nmox.studio.rack.service;

import javax.swing.JEditorPane;
import javax.swing.text.Document;
import org.nmox.studio.core.util.EditedFile;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.windows.TopComponent;

/**
 * The file an editor TAB holds — the tab-shaped twin of
 * {@link EditedFile#of(javax.swing.text.Document)}. A tab's lookup carries
 * its DataObject, and for a grouped DataObject (a locale's
 * {@code Bundle_de.properties} under {@code Bundle.properties}) that names
 * the group, not the file: the properties module's editor puts only
 * itself in the tab's lookup (its {@code PropertiesEditorLookup}, read from
 * the RELEASE310 bytecode), so the file is told from the editor's DOCUMENT
 * (3.2 sixth review: the Agent Port listed, Annotate annotated and Save
 * Editor Screenshot named the base bundle while the German one was on
 * screen).
 */
public final class EditorTabs {

    private EditorTabs() {
    }

    /** The file {@code tc} holds, or null (no DataObject, a folder, or no way to tell which). EDT only. */
    public static FileObject fileOf(TopComponent tc) {
        if (tc == null) {
            return null;
        }
        DataObject dob = tc.getLookup().lookup(DataObject.class);
        if (dob == null) {
            return null;
        }
        FileObject fo = EditedFile.of(dob, documentOf(tc));
        return fo == null || fo.isFolder() ? null : fo;
    }

    /**
     * The document of the tab's editor when that editor has been built;
     * null otherwise — never builds one ({@code getEditorPane} answers null
     * before the component exists).
     */
    static Document documentOf(TopComponent tc) {
        CloneableEditorSupport.Pane pane = tc instanceof CloneableEditorSupport.Pane p ? p
                : tc.getLookup().lookup(CloneableEditorSupport.Pane.class);
        JEditorPane editor = pane == null ? null : pane.getEditorPane();
        return editor == null ? null : editor.getDocument();
    }
}

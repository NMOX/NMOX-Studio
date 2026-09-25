package org.nmox.studio.editor.a11y;

import java.beans.PropertyChangeListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.EditorRegistry;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.modules.OnStart;

/**
 * Every editor tells a screen reader which file it is (3.2.0).
 *
 * <p>The platform's editor pane ({@code org.openide.text.QuietEditorPane})
 * answers every accessibility query with the name {@code "Editor for " +
 * getName()} - read from its RELEASE310 bytecode - and nothing in the
 * platform ever gives the pane a name, so VoiceOver read "Editor for null"
 * for every file in the IDE (found walking {@code nmox -w} with the
 * accessibility tree, 3.2.0). This names each pane after the file its
 * document came from the moment the pane is registered with the editor
 * registry (which it is when it first takes focus, before anyone can be
 * reading it), and only a pane that has no name yet: a component name set
 * by anyone else is left alone.
 *
 * <p>Zero boot cost: one listener on the editor registry, which exists
 * whether or not an editor ever opens.
 */
@OnStart
public final class EditorAccessibleNames implements Runnable {

    private static final PropertyChangeListener NAMER = e -> nameAll();

    @Override
    public void run() {
        EditorRegistry.addPropertyChangeListener(NAMER);
    }

    /** Names every registered editor pane that has no name yet. */
    static void nameAll() {
        for (JTextComponent c : EditorRegistry.componentList()) {
            name(c);
        }
    }

    /** Marks a name this class gave, so a rename can follow the file. */
    static final String OURS = "nmox.a11y.namedAfterFile";

    /**
     * Gives {@code c} its document's file name when it has none, and keeps
     * a name this class gave current: after a rename the next focus names
     * the pane after the file it now shows. A name anyone else set is kept.
     */
    static void name(JTextComponent c) {
        if (c.getName() != null && c.getClientProperty(OURS) == null) {
            return;
        }
        String file = fileName(c.getDocument());
        if (file != null && !file.equals(c.getName())) {
            c.setName(file);
            c.putClientProperty(OURS, Boolean.TRUE);
        }
    }

    /**
     * The name of the file {@code doc} was loaded from - {@code app.js},
     * {@code COMMIT_EDITMSG} - or null for a document with no file behind
     * it (an output pane, a dialog's text field).
     */
    static String fileName(Document doc) {
        if (doc == null) {
            return null;
        }
        Object sdp = doc.getProperty(Document.StreamDescriptionProperty);
        // the file edited, not its group's primary (a Bundle_de.properties
        // editor is not "Editor for Bundle.properties")
        FileObject fo = sdp instanceof DataObject ? org.nmox.studio.editor.share.EditedFile.of(doc)
                : sdp instanceof FileObject f ? f : null;
        return fo == null ? null : fo.getNameExt();
    }
}

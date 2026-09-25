package org.nmox.studio.core.util;

import java.util.Collection;
import javax.swing.text.Document;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 * The file an editor's document holds — not its DataObject's primary file.
 *
 * <p>A document names its DataObject ({@link Document#StreamDescriptionProperty}),
 * and one DataObject can own several files: the properties module groups
 * {@code Bundle_de.properties} under {@code Bundle.properties} and gives each
 * locale's editor a document whose stream is the WHOLE group (its
 * {@code PropertiesEditorSupport.createStyledDocument}, read from the
 * RELEASE310 bytecode). Taking the primary file there named
 * {@code Bundle.properties} while the user edited the German file — Copy
 * Path copied the wrong path, and line blame blamed the wrong file's line
 * (3.2 fourth review). The same editor support writes the entry's own file
 * into {@link Document#TitleProperty}, as {@code FileObject.toString()}, so
 * that is how the file is told apart; a DataObject of several files whose
 * document names none of them answers null — every caller then says
 * nothing rather than naming the wrong file.
 */
public final class EditedFile {

    private EditedFile() {
    }

    /** The file {@code doc} holds, or null (no document, no file, or no way to tell which). */
    public static FileObject of(Document doc) {
        Object sd = doc == null ? null : doc.getProperty(Document.StreamDescriptionProperty);
        if (!(sd instanceof DataObject dob)) {
            return null;
        }
        return pick(doc.getProperty(Document.TitleProperty), dob.getPrimaryFile(), dob.files());
    }

    /** The rule, with the platform left out. */
    static FileObject pick(Object title, FileObject primary, Collection<FileObject> files) {
        if (files == null || files.size() <= 1) {
            return primary; // one file: the primary IS the file edited
        }
        if (title instanceof String t) {
            for (FileObject fo : files) {
                if (fo != null && (t.equals(fo.toString()) || t.equals(fo.getPath())
                        || t.equals(org.openide.filesystems.FileUtil.getFileDisplayName(fo)))) {
                    return fo;
                }
            }
        }
        return null;
    }
}

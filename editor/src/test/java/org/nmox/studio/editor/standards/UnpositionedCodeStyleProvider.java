package org.nmox.studio.editor.standards;

import java.util.prefs.Preferences;
import javax.swing.text.Document;
import org.netbeans.modules.editor.indent.spi.CodeStylePreferences;
import org.openide.filesystems.FileObject;

/**
 * A test-only stand-in for the platform's project-aware provider, which
 * the shipped cluster registers the same way: a plain
 * {@code META-INF/services} line with no {@code #position}. It answers
 * nothing, so the documents in these tests fall through it exactly as
 * they would fall through a provider with nothing to say - and its only
 * job is to be something {@link EditorConfigCodeStyle} must be ordered
 * ahead of.
 */
public final class UnpositionedCodeStyleProvider implements CodeStylePreferences.Provider {

    @Override
    public Preferences forFile(FileObject file, String mimeType) {
        return null;
    }

    @Override
    public Preferences forDocument(Document doc, String mimeType) {
        return null;
    }
}

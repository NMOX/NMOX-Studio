package org.nmox.studio.editor.fullstack;

import java.io.File;
import java.util.EnumSet;
import java.util.Set;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt;
import org.netbeans.lib.editor.hyperlink.spi.HyperlinkType;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.RequestProcessor;

/**
 * The shared skeleton of a project-resolving hyperlink (v2.31.0):
 * span math inline on the edit-version-cached document text (the
 * v1.234.0 hover law), the project resolution off the EDT on a named
 * RP with only the jump hopping back. Extracted when the fullstack
 * pair became the third and fourth copies of the design package's
 * hyperlink plumbing (the FilePulse promote-on-second-copy law,
 * overdue by two); the design pair's migration is a review candidate,
 * not a mid-batch edit.
 */
public abstract class ProjectJumpHyperlink implements HyperlinkProviderExt {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-fullstack-jump", 1);
    private static final int MAX_SCAN_CHARS = 500_000;

    /** The span under the offset, or null — pure over the text. */
    protected abstract int[] spanAt(String text, int offset);

    /** Resolve + jump (called OFF the EDT); status refusals inside. */
    protected abstract void click(String text, int[] span, File projectDir);

    protected abstract String tooltip();

    @Override
    public final Set<HyperlinkType> getSupportedHyperlinkTypes() {
        return EnumSet.of(HyperlinkType.GO_TO_DECLARATION);
    }

    @Override
    public final boolean isHyperlinkPoint(Document doc, int offset, HyperlinkType type) {
        String text = textOf(doc);
        return text != null && spanAt(text, offset) != null;
    }

    @Override
    public final int[] getHyperlinkSpan(Document doc, int offset, HyperlinkType type) {
        String text = textOf(doc);
        return text == null ? null : spanAt(text, offset);
    }

    @Override
    public final String getTooltipText(Document doc, int offset, HyperlinkType type) {
        return tooltip();
    }

    @Override
    public final void performClickAction(Document doc, int offset, HyperlinkType type) {
        String text = textOf(doc);
        int[] span = text == null ? null : spanAt(text, offset);
        if (span == null) {
            return;
        }
        File dir = projectDirOf(doc);
        RP.post(() -> click(text, span, dir));
    }

    /** Open {@code file} at {@code offset}'s line, from any thread. */
    protected static void openAt(File file, int offset) {
        java.awt.EventQueue.invokeLater(() -> {
            try {
                FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
                if (fo == null) {
                    return;
                }
                String text = fo.asText();
                int line = 0;
                for (int i = 0; i < Math.min(offset, text.length()); i++) {
                    if (text.charAt(i) == '\n') {
                        line++;
                    }
                }
                LineCookie lc = DataObject.find(fo).getLookup().lookup(LineCookie.class);
                if (lc != null) {
                    lc.getLineSet().getCurrent(line)
                            .show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
                }
            } catch (Exception ex) {
                StatusDisplayer.getDefault().setStatusText(
                        "Could not open " + file.getName() + ": " + ex.getMessage());
            }
        });
    }

    protected static void status(String message) {
        java.awt.EventQueue.invokeLater(
                () -> StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(message)));
    }

    // ---- document text, edit-version cached -------------------------------

    private String textOf(Document doc) {
        if (doc.getLength() > MAX_SCAN_CHARS) {
            return null;
        }
        Cache cache = (Cache) doc.getProperty(getClass());
        if (cache == null) {
            cache = new Cache();
            doc.putProperty(getClass(), cache);
            doc.addDocumentListener(cache);
        }
        long version = cache.version.get();
        if (cache.cachedVersion == version) {
            return cache.text;
        }
        try {
            cache.text = doc.getText(0, doc.getLength());
            cache.cachedVersion = version;
            return cache.text;
        } catch (BadLocationException ex) {
            return null;
        }
    }

    private static final class Cache implements javax.swing.event.DocumentListener {

        final java.util.concurrent.atomic.AtomicLong version =
                new java.util.concurrent.atomic.AtomicLong();
        volatile long cachedVersion = -1;
        volatile String text = "";

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            version.incrementAndGet();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            version.incrementAndGet();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
        }
    }

    /** The file's project root (marker walk, capped). */
    protected static File projectDirOf(Document doc) {
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        File f = fo == null ? null : FileUtil.toFile(fo);
        if (f == null) {
            return null;
        }
        File dir = f.getParentFile();
        File cursor = dir;
        for (int up = 0; cursor != null && up < 6; up++, cursor = cursor.getParentFile()) {
            if (new File(cursor, "package.json").isFile()
                    || new File(cursor, "angular.json").isFile()
                    || new File(cursor, ".git").exists()) {
                return cursor;
            }
        }
        return dir;
    }
}

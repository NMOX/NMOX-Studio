package org.nmox.studio.editor.design;

import java.io.File;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
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
 * ⌘-click a {@code var(--token)} usage and land on the token's
 * declaration (v1.330.0) — same document first, then the project's
 * stylesheets through {@link CssTokens}' bounded scan. The designer's
 * "where is this token defined?" becomes one click, the same gesture
 * code already has everywhere else.
 *
 * <p>Same-document resolution happens inline (a string scan of an open
 * document); the PROJECT fallback walks disk, so it rides a named RP
 * with only the jump hopping back to the EDT — the v1.220.0 law about
 * hyperlink actions that touch disk.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/css", service = HyperlinkProviderExt.class, position = 140),
    @MimeRegistration(mimeType = "text/scss", service = HyperlinkProviderExt.class, position = 140),
    @MimeRegistration(mimeType = "text/less", service = HyperlinkProviderExt.class, position = 140),
    @MimeRegistration(mimeType = "text/x-scss", service = HyperlinkProviderExt.class, position = 140),
    @MimeRegistration(mimeType = "text/x-sass", service = HyperlinkProviderExt.class, position = 140),
    @MimeRegistration(mimeType = "text/x-less", service = HyperlinkProviderExt.class, position = 140)
})
public final class CssVarHyperlink implements HyperlinkProviderExt {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-css-token-jump", 1);
    private static final int MAX_SCAN_CHARS = 500_000;

    @Override
    public Set<HyperlinkType> getSupportedHyperlinkTypes() {
        return EnumSet.of(HyperlinkType.GO_TO_DECLARATION);
    }

    @Override
    public boolean isHyperlinkPoint(Document doc, int offset, HyperlinkType type) {
        return spanAt(doc, offset) != null;
    }

    @Override
    public int[] getHyperlinkSpan(Document doc, int offset, HyperlinkType type) {
        return spanAt(doc, offset);
    }

    @Override
    public String getTooltipText(Document doc, int offset, HyperlinkType type) {
        return "Go to the token's declaration";
    }

    @Override
    public void performClickAction(Document doc, int offset, HyperlinkType type) {
        String text = textOf(doc);
        int[] span = text == null ? null : CssTokens.varNameSpanAt(text, offset);
        if (span == null) {
            return;
        }
        String name = text.substring(span[0], span[1]);
        // same document: jump directly, no disk involved
        Map<String, CssTokens.Token> local = CssTokens.declarations(text);
        CssTokens.Token here = local.get(name);
        if (here != null) {
            jumpTo(doc, here.offset());
            return;
        }
        // elsewhere in the project: resolve off the EDT, jump on it
        File dir = projectDirOf(doc);
        RP.post(() -> {
            CssTokens.ProjectToken found = CssTokens.scanProject(dir).stream()
                    .filter(t -> t.name().equals(name))
                    .findFirst().orElse(null);
            java.awt.EventQueue.invokeLater(() -> {
                if (found == null) {
                    StatusDisplayer.getDefault().setStatusText(
                            name + " is not declared in this project's stylesheets");
                } else {
                    openAt(found.file(), found.offset());
                }
            });
        });
    }

    // ---- helpers ---------------------------------------------------------

    private static int[] spanAt(Document doc, int offset) {
        String text = textOf(doc);
        return text == null ? null : CssTokens.varNameSpanAt(text, offset);
    }

    /**
     * Document text, cached by edit-version (v1.333.0 review): the
     * platform calls {@code isHyperlinkPoint}/{@code getHyperlinkSpan}
     * synchronously ON THE EDT for every ⌘-mouse-move, and this used to
     * pay a full {@code getText} copy per hover — the exact class the
     * v1.234.0 review fixed in {@link CssColorHyperlink}, whose javadoc
     * carries the whole rationale, regressed here in day-old code. Same
     * cure: a document-property cache whose version listener is held
     * only by the document (collected with it), EDT-confined by the
     * hyperlink SPI's contract.
     */
    private static String textOf(Document doc) {
        if (doc.getLength() > MAX_SCAN_CHARS) {
            return null;
        }
        TextCache cache = (TextCache) doc.getProperty(TextCache.class);
        if (cache == null) {
            cache = new TextCache();
            doc.putProperty(TextCache.class, cache);
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

    /** Edit counter + memoized text; attribute-only changes don't bump. */
    private static final class TextCache implements javax.swing.event.DocumentListener {

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

    /**
     * The stylesheet's project root: walk UP from the file until a
     * project marker (package.json, angular.json, .git) appears, capped
     * so a file in / cannot send the scan across the disk. No marker →
     * the file's own folder, which still covers the tokens-beside-the-
     * stylesheet layout.
     */
    private static File projectDirOf(Document doc) {
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

    /** Jump inside the ALREADY-OPEN document. */
    private static void jumpTo(Document doc, int offset) {
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        if (fo != null) {
            openAt(FileUtil.toFile(fo), offset);
        }
    }

    /** Open {@code file} with the caret on {@code offset}'s line (EDT). */
    private static void openAt(File file, int offset) {
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
    }
}

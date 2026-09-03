package org.nmox.studio.editor.angular;

import java.io.File;
import java.util.EnumSet;
import java.util.Set;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.netbeans.api.editor.mimelookup.MimeRegistration;
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
 * ⌘-click {@code <app-hero>} in a template and land on the component
 * that declares that selector (the Angular-top arc, 2026-08-11). This
 * is the daily Angular navigation gesture, and it works WITHOUT the
 * Angular Language Service — the v1.219.0 ⌘B rides ngserver and goes
 * dead when it isn't installed; this provider rides {@link
 * NgSelectors}' own index, so the gesture is alive on a bare install.
 * Position 90 — BEFORE the v1.219.0 ALS enabler at 100, which claims
 * any identifier span and delegates to the language-server provider:
 * without a running server that delegation dies silently, swallowing
 * the click (proven live, first round of this arc). This provider's
 * claim is narrow — dashed tags only — so plain identifiers still
 * flow to the enabler and ALS; a dashed-tag jump lands where ALS
 * would have landed anyway: the component that declares the selector.
 *
 * <p>Hover work is EDT work: the span test is a pure text parse (a
 * dashed tag name under the caret — Angular convention guarantees the
 * dash in practice; dashless selectors are a recorded miss), document
 * text cached by edit-version (the v1.234.0 law). The DISK lookup
 * happens only on click, on a named RP, with an honest status miss.
 */
@MimeRegistration(mimeType = "text/x-ng-template",
        service = HyperlinkProviderExt.class, position = 90)
public final class NgSelectorHyperlink implements HyperlinkProviderExt {

    // -J-Dorg.nmox.studio.editor.level=FINE lands these in messages.log (v2.63.0: the platform logger, not stderr)
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(NgSelectorHyperlink.class.getName());

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-ng-selector-jump", 1);
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
        return "Go to the component that declares this selector";
    }

    /**
     * The chord entry (⌘B rides this — the proven navigation path on
     * this CSL mime): true when the caret sits on a dashed tag and the
     * jump was DISPATCHED (resolution is async; a miss reports on the
     * status line). False = not a tag, caller should try its next path.
     */
    public static boolean jumpToSelector(Document doc, int offset) {
        String text = textOf(doc);
        int[] span = text == null ? null : tagSpanAt(text, offset);
        if (span == null) {
            return false;
        }
        dispatchJump(doc, text.substring(span[0], span[1]));
        return true;
    }

    @Override
    public void performClickAction(Document doc, int offset, HyperlinkType type) {
        String text = textOf(doc);
        int[] span = text == null ? null : tagSpanAt(text, offset);
        if (span == null) {
            return;
        }
        dispatchJump(doc, text.substring(span[0], span[1]));
    }

    private static void dispatchJump(Document doc, String tag) {
        LOG.fine(() -> "[ng-probe] dispatchJump tag=" + tag);
        File root = projectDirOf(doc);
        RP.post(() -> {
            NgSelectors.Decl found = NgSelectors.find(root, tag);
            java.awt.EventQueue.invokeLater(() -> {
                if (found == null) {
                    StatusDisplayer.getDefault().setStatusText(
                            "No component in this project declares the selector \""
                            + tag + '"');
                } else {
                    openAt(found.file(), found.offset());
                }
            });
        });
    }

    // ---- span parsing ----------------------------------------------------

    private static int[] spanAt(Document doc, int offset) {
        String text = textOf(doc);
        return text == null ? null : tagSpanAt(text, offset);
    }

    /**
     * The DASHED tag name under {@code offset}, in an opening or
     * closing tag ({@code <app-x>}, {@code </app-x>}), else null. Pure
     * text; package-visible for tests.
     */
    static int[] tagSpanAt(String text, int offset) {
        if (offset < 0 || offset > text.length()) {
            return null;
        }
        int start = offset;
        while (start > 0 && isTagChar(text.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < text.length() && isTagChar(text.charAt(end))) {
            end++;
        }
        if (start >= end) {
            return null;
        }
        // must sit directly after < or </ — attribute names also carry
        // dashes and must not underline
        int before = start - 1;
        boolean afterSlash = before >= 0 && text.charAt(before) == '/';
        int anglePos = afterSlash ? before - 1 : before;
        if (anglePos < 0 || text.charAt(anglePos) != '<') {
            return null;
        }
        String tag = text.substring(start, end);
        if (tag.indexOf('-') <= 0) {
            return null; // <div>, <h1>: platform tags are not jump targets
        }
        return new int[]{start, end};
    }

    private static boolean isTagChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-';
    }

    // ---- document text, edit-version cached (v1.234 law) -----------------

    static String textOf(Document doc) {
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

    // ---- project + open --------------------------------------------------

    private static File projectDirOf(Document doc) {
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        File f = fo == null ? null : FileUtil.toFile(fo);
        return f == null ? null : projectDirAbove(f.getParentFile());
    }

    /** The nearest enclosing workspace root above {@code dir} (shared with the CSL finder). */
    static File projectDirAbove(File dir) {
        File cursor = dir;
        for (int up = 0; cursor != null && up < 8; up++, cursor = cursor.getParentFile()) {
            if (new File(cursor, "angular.json").isFile()
                    || new File(cursor, "package.json").isFile()
                    || new File(cursor, ".git").exists()) {
                return cursor;
            }
        }
        return dir;
    }

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

package org.nmox.studio.editor.design;

import java.io.File;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
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
 * The reverse of {@link CssClassHyperlink} (v2.28.0): ⌘-click a
 * {@code .name} selector in a STYLESHEET and land on the class's first
 * {@code class="…"} usage in the project's markup — "who uses this
 * rule?" as the same gesture as everything else. More than one usage
 * says so on the status line with the count (a picker is a recorded
 * non-goal until the gesture earns one); none refuses honestly.
 *
 * <p>Same threading law as its twin (v1.220.0): the span test is an
 * inline scan of the open document; the project usage sweep reads
 * disk on a named RP with only the jump hopping back to the EDT.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/css", service = HyperlinkProviderExt.class, position = 150),
    @MimeRegistration(mimeType = "text/scss", service = HyperlinkProviderExt.class, position = 150),
    @MimeRegistration(mimeType = "text/less", service = HyperlinkProviderExt.class, position = 150),
    @MimeRegistration(mimeType = "text/x-scss", service = HyperlinkProviderExt.class, position = 150),
    @MimeRegistration(mimeType = "text/x-less", service = HyperlinkProviderExt.class, position = 150),
    @MimeRegistration(mimeType = "text/x-sass", service = HyperlinkProviderExt.class, position = 150)
})
public final class CssClassUsageHyperlink implements HyperlinkProviderExt {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-css-class-usages", 1);
    private static final int MAX_SCAN_CHARS = 500_000;
    private static final int USAGE_CAP = 50;

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
        return "Go to the class's usages in markup";
    }

    @Override
    public void performClickAction(Document doc, int offset, HyperlinkType type) {
        String text = textOf(doc);
        int[] span = text == null ? null : CssClasses.selectorSpanAt(text, offset);
        if (span == null) {
            return;
        }
        String name = text.substring(span[0], span[1]);
        File dir = projectDirOf(doc);
        RP.post(() -> {
            List<CssClasses.Usage> usages =
                    CssClasses.findUsages(dir, name, USAGE_CAP);
            java.awt.EventQueue.invokeLater(() -> {
                if (usages.isEmpty()) {
                    StatusDisplayer.getDefault().setStatusText("." + name
                            + " has no class=\"\" usages in this project's markup");
                    return;
                }
                CssClasses.Usage first = usages.get(0);
                openAt(first.file(), first.offset());
                if (usages.size() > 1) {
                    Set<String> files = new LinkedHashSet<>();
                    for (CssClasses.Usage u : usages) {
                        files.add(u.file().getName());
                    }
                    StatusDisplayer.getDefault().setStatusText(
                            usages.size() + " usages in " + files.size()
                            + " file(s) — opened the first"
                            + (usages.size() >= USAGE_CAP ? " (list capped)" : ""));
                }
            });
        });
    }

    // ---- helpers (the family's shared shapes) -----------------------------

    private static int[] spanAt(Document doc, int offset) {
        String text = textOf(doc);
        return text == null ? null : CssClasses.selectorSpanAt(text, offset);
    }

    /** Edit-version-cached document text (the v1.234.0 hover law). */
    private static String textOf(Document doc) {
        if (doc.getLength() > MAX_SCAN_CHARS) {
            return null;
        }
        Cache cache = (Cache) doc.getProperty(Cache.class);
        if (cache == null) {
            cache = new Cache();
            doc.putProperty(Cache.class, cache);
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

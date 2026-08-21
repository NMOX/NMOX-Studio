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
 * ⌘-click a class name inside {@code class="…"} and land on its rule
 * (v2.27.0) — this file's own {@code <style>} blocks first, then the
 * project's stylesheets through {@link CssClasses}' bounded scan. The
 * markup twin of {@link CssVarHyperlink}, and the same threading law:
 * same-document resolution is an inline string scan; the project
 * fallback walks disk on a named RP with only the jump hopping back to
 * the EDT (v1.220.0).
 *
 * <p>Position 10 — deliberately AHEAD of the platform's CSL/GSF
 * hyperlink (HtmlDeclarationFinder), which also claims class attributes
 * but resolves only against LINKED stylesheets and answers "Class …
 * not found" for the everyday case of a project stylesheet the page
 * does not link (found live, v2.27.0 walk). This provider's claim is
 * the narrowest possible (class-attribute values only), so every other
 * hyperlink in the file still reaches its owner. (Decompiled truth,
 * this walk: HyperlinkOperation.findProvider consults the document's
 * TOP mime only — embedded-mime registrations would be dead rows — and
 * the ⌘-click leg is unverifiable by synthesized automation, which
 * cannot carry the modifier into Swing's mouse event: the live proof
 * is the hover consultation reaching this provider with the exact
 * class-name span, pinned in the walk log; the ledger-76 class.)
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/html", service = HyperlinkProviderExt.class, position = 10),
    @MimeRegistration(mimeType = "text/x-vue", service = HyperlinkProviderExt.class, position = 10),
    @MimeRegistration(mimeType = "text/x-svelte", service = HyperlinkProviderExt.class, position = 10),
    @MimeRegistration(mimeType = "text/x-ng-template", service = HyperlinkProviderExt.class, position = 10)
})
public final class CssClassHyperlink implements HyperlinkProviderExt {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-css-class-jump", 1);
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
        return "Go to the class's rule";
    }

    @Override
    public void performClickAction(Document doc, int offset, HyperlinkType type) {
        String text = textOf(doc);
        int[] span = text == null ? null : CssClasses.attrNameSpanAt(text, offset);
        if (span == null) {
            return;
        }
        String name = text.substring(span[0], span[1]);
        // this file's own <style> blocks: jump directly, no disk
        for (HtmlStyleRegions.Region r : HtmlStyleRegions.find(text)) {
            Map<String, CssClasses.Selector> local =
                    CssClasses.selectors(text.substring(r.start(), r.end()));
            CssClasses.Selector here = local.get(name);
            if (here != null) {
                jumpTo(doc, here.offset() + r.start());
                return;
            }
        }
        // elsewhere in the project: resolve off the EDT, jump on it
        File dir = projectDirOf(doc);
        RP.post(() -> {
            CssClasses.ProjectSelector found = CssClasses.scanProject(dir).stream()
                    .filter(s -> s.name().equals(name))
                    .findFirst().orElse(null);
            java.awt.EventQueue.invokeLater(() -> {
                if (found == null) {
                    StatusDisplayer.getDefault().setStatusText("." + name
                            + " is not declared in this project's stylesheets");
                } else {
                    openAt(found.file(), found.offset());
                }
            });
        });
    }

    // ---- helpers (the CssVarHyperlink shapes, markup-side) ----------------

    private static int[] spanAt(Document doc, int offset) {
        String text = textOf(doc);
        return text == null ? null : CssClasses.attrNameSpanAt(text, offset);
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

    private static void jumpTo(Document doc, int offset) {
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        if (fo != null) {
            openAt(FileUtil.toFile(fo), offset);
        }
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

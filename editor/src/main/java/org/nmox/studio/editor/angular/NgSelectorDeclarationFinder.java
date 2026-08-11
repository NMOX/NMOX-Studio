package org.nmox.studio.editor.angular;

import java.io.File;

import javax.swing.text.Document;

import org.netbeans.modules.csl.api.DeclarationFinder;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.spi.ParserResult;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * The NATIVE ⌘B for Angular templates (Angular-top arc, 2026-08-11):
 * CSL's own Go to Declaration — the chord, the Navigate menu item, and
 * CSL's ⌘-hover underline — consults this finder, so the standard
 * gesture jumps from {@code <app-hero>} to the component declaring the
 * selector without any custom action in the path. Resolution rides
 * {@link NgSelectors}' decorator-gated index, so it works with no
 * language service installed.
 *
 * <p>{@link #getReferenceSpan} runs on the EDT per caret move — it is a
 * pure text parse over the v1.234.0 edit-version cache. The disk scan
 * happens in {@link #findDeclaration}, which CSL calls on a parser
 * task thread.
 */
public final class NgSelectorDeclarationFinder implements DeclarationFinder {

    @Override
    public DeclarationLocation findDeclaration(ParserResult info, int caretOffset) {
        String text = info.getSnapshot().getText().toString();
        int[] span = NgSelectorHyperlink.tagSpanAt(text, caretOffset);
        if (span == null) {
            // the ledger-78 identifier half: a non-tag identifier goes to
            // ngserver — the lsp-client's provider navigates ITSELF, and
            // CSL is quiet on NONE (decompiled: no status, no beep), so
            // triggering the jump here and returning NONE is clean. The
            // shipped lsp-client implements no HyperlinkLocationProvider,
            // so a location-returning route does not exist to prefer.
            javax.swing.text.Document doc =
                    info.getSnapshot().getSource().getDocument(false);
            if (doc != null
                    && org.nmox.studio.editor.lsp.NgTemplateHyperlinkEnabler
                            .identifierSpan(doc, caretOffset) != null) {
                org.netbeans.lib.editor.hyperlink.spi.HyperlinkProviderExt lsp =
                        org.nmox.studio.editor.lsp.NgTemplateHyperlinkEnabler
                                .lspProvider();
                if (lsp != null) {
                    lsp.performClickAction(doc, caretOffset,
                            org.netbeans.lib.editor.hyperlink.spi.HyperlinkType
                                    .GO_TO_DECLARATION);
                }
            }
            return DeclarationLocation.NONE;
        }
        String tag = text.substring(span[0], span[1]);
        FileObject fo = info.getSnapshot().getSource().getFileObject();
        File f = fo == null ? null : FileUtil.toFile(fo);
        File root = f == null ? null
                : NgSelectorHyperlink.projectDirAbove(f.getParentFile());
        NgSelectors.Decl decl = NgSelectors.find(root, tag);
        if (decl == null) {
            return DeclarationLocation.NONE;
        }
        FileObject target = FileUtil.toFileObject(FileUtil.normalizeFile(decl.file()));
        return target == null ? DeclarationLocation.NONE
                : new DeclarationLocation(target, decl.offset());
    }

    @Override
    public OffsetRange getReferenceSpan(Document doc, int caretOffset) {
        String text = NgSelectorHyperlink.textOf(doc);
        int[] span = text == null ? null
                : NgSelectorHyperlink.tagSpanAt(text, caretOffset);
        if (span != null) {
            return new OffsetRange(span[0], span[1]);
        }
        // identifiers must be CLAIMED here or CSL never calls
        // findDeclaration for them and ⌘B stays dead on {{ user.name }};
        // claim only when a language server can actually answer
        int[] id = org.nmox.studio.editor.lsp.NgTemplateHyperlinkEnabler
                .identifierSpan(doc, caretOffset);
        if (id != null && org.nmox.studio.editor.lsp.NgTemplateHyperlinkEnabler
                .lspProvider() != null) {
            return new OffsetRange(id[0], id[1]);
        }
        return OffsetRange.NONE;
    }
}

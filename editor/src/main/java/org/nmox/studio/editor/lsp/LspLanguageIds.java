package org.nmox.studio.editor.lsp;

import java.util.Map;

import org.netbeans.modules.lsp.client.spi.LanguageIdResolver;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Maps NetBeans mimes to the LSP-standard language identifiers servers
 * actually key on (v1.218.0).
 *
 * <p><b>The bug this fixes.</b> Without a {@link LanguageIdResolver} in
 * the server description's lookup, the platform LSP client sends the RAW
 * MIME TYPE as {@code textDocument/didOpen}'s languageId (decompiled:
 * {@code LSPBindings.resolveLanguageId} falls back to
 * {@code FileUtil.getMIMEType}). Servers classify documents by the
 * VS Code language-identifier vocabulary — {@code typescript},
 * {@code html}, {@code python} — so a server receiving
 * {@code text/typescript} or {@code text/x-ng-template} may silently
 * ignore the document: the Angular Language Service treats external
 * templates as templates only when they arrive as {@code html}.
 *
 * <p>The mapping is one generic rule — the mime subtype with any
 * {@code x-} prefix stripped, which is already correct for most of the
 * family ({@code text/x-python} → {@code python}) — plus an explicit
 * table for the exceptions where our mime name and the LSP identifier
 * genuinely differ.
 */
public final class LspLanguageIds implements LanguageIdResolver {

    /**
     * Mimes whose stripped subtype is NOT the LSP identifier. Kept
     * deliberately small: everything not listed rides the generic rule.
     */
    static final Map<String, String> EXCEPTIONS = Map.ofEntries(
            // Angular external templates: ngserver only treats a document
            // as a template when it arrives as html
            Map.entry("text/x-ng-template", "html"),
            // LSP calls shell "shellscript" (bash-language-server keys on it)
            Map.entry("text/sh", "shellscript"),
            Map.entry("text/x-php5", "php"),
            // v-analyzer's identifier is "v", not the mime's "vlang"
            Map.entry("text/x-vlang", "v"));

    @Override
    public String resolveLanguageId(FileObject file) {
        return forMime(FileUtil.getMIMEType(file));
    }

    /** Pure mapping used by {@link #resolveLanguageId} and its tests. */
    public static String forMime(String mime) {
        if (mime == null || !mime.contains("/")) {
            return null; // client falls back to its own default
        }
        String override = EXCEPTIONS.get(mime);
        if (override != null) {
            return override;
        }
        String subtype = mime.substring(mime.indexOf('/') + 1);
        return subtype.startsWith("x-") ? subtype.substring(2) : subtype;
    }
}

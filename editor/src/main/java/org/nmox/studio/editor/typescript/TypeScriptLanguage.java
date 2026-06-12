package org.nmox.studio.editor.typescript;

import org.netbeans.api.lexer.Language;
import org.nmox.studio.editor.javascript.JavaScriptLanguageHierarchy;
import org.nmox.studio.editor.javascript.JavaScriptTokenId;

/**
 * The lexer Language for text/typescript. TypeScript reuses the
 * JavaScript lexer wholesale - TS-only keywords (interface, type,
 * enum...) are already in the keyword table - bound to its own MIME
 * type through a hierarchy that only overrides mimeType().
 */
public final class TypeScriptLanguage {

    private TypeScriptLanguage() {
    }

    /** Lazy holder, same cycle-avoidance as JavaScriptTokenId.language(). */
    private static final class Holder {
        static final Language<JavaScriptTokenId> LANGUAGE = new Hierarchy().language();
    }

    public static Language<JavaScriptTokenId> language() {
        return Holder.LANGUAGE;
    }

    private static final class Hierarchy extends JavaScriptLanguageHierarchy {
        @Override
        protected String mimeType() {
            return "text/typescript";
        }
    }
}

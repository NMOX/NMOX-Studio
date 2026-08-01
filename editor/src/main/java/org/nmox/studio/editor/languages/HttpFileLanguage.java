package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * {@code .http}/{@code .rest} request files (text/x-http-request) as a
 * CSL language (v1.217.0).
 *
 * <p>The v1.166.0 grammar registration was "grammar-only by design" —
 * but a grammar-only mime has no loader and no kit, so the files have
 * opened with the PLAIN kit and the vendored REST-Client grammar has
 * never actually reached the screen. Found while debugging the same
 * gap in the Angular-template mime; this registration is what makes
 * the mime real (see {@link NgTemplateLanguage} for the mechanism).
 */
@LanguageRegistration(mimeType = "text/x-http-request")
public class HttpFileLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Lexers.find("text/x-http-request");
    }

    @Override
    public String getDisplayName() {
        return "HTTP Request";
    }

    @Override
    public String getLineCommentPrefix() {
        return "#";
    }
}

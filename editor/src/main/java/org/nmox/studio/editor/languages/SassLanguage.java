package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Indented Sass as a first-class CSL language (v2.20.0); tokens come
 * from the TextMate grammar registered for this MIME — a grammar
 * alone does not make a mime (the v1.217.0 lesson), so this class is
 * what gives .sass a real editor kit.
 */
@LanguageRegistration(mimeType = "text/x-sass")
public class SassLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Lexers.find("text/x-sass");
    }

    @Override
    public String getDisplayName() {
        return "Sass";
    }

    @Override
    public String getLineCommentPrefix() {
        return "//";
    }
}

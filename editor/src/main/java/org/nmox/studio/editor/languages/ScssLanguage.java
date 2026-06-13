package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * SCSS as a first-class CSL language; tokens come from the TextMate
 * grammar registered for this MIME.
 */
@LanguageRegistration(mimeType = "text/x-scss")
public class ScssLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Language.find("text/x-scss");
    }

    @Override
    public String getDisplayName() {
        return "SCSS";
    }

    @Override
    public String getLineCommentPrefix() {
        return "//";
    }
}

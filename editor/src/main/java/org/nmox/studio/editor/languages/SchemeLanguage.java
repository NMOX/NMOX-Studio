package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Scheme as a first-class CSL language: the platform supplies the
 * editor kit, bracket logic and folding hooks; the TextMate grammar
 * (registered for this MIME) supplies the tokens.
 */
@LanguageRegistration(mimeType = "text/x-scheme")
public class SchemeLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Language.find("text/x-scheme");
    }

    @Override
    public String getDisplayName() {
        return "Scheme";
    }

    @Override
    public String getLineCommentPrefix() {
        return ";";
    }
}

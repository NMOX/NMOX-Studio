package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Prolog as a first-class CSL language: the platform supplies the
 * editor kit, bracket logic and folding hooks; the TextMate grammar
 * (registered for this MIME) supplies the tokens.
 */
@LanguageRegistration(mimeType = "text/x-prolog")
public class PrologLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Lexers.find("text/x-prolog");
    }

    @Override
    public String getDisplayName() {
        return "Prolog";
    }

    @Override
    public String getLineCommentPrefix() {
        return "%";
    }
}

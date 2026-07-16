package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * DLang as a first-class CSL language: the platform supplies the
 * editor kit, comment toggling, bracket logic and folding hooks; the
 * TextMate grammar (registered for this MIME) supplies the tokens.
 */
@LanguageRegistration(mimeType = "text/x-d")
public class DLangLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Language.find("text/x-d");
    }

    @Override
    public String getDisplayName() {
        return "D";
    }

    @Override
    public String getLineCommentPrefix() {
        return "//";
    }
}

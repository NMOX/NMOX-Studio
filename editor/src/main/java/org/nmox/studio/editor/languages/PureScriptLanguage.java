package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * PureScript as a first-class CSL language: the platform supplies the
 * editor kit, comment toggling, bracket logic and folding hooks; the
 * TextMate grammar (registered for this MIME) supplies the tokens.
 */
@LanguageRegistration(mimeType = "text/x-purescript")
public class PureScriptLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Language.find("text/x-purescript");
    }

    @Override
    public String getDisplayName() {
        return "PureScript";
    }

    @Override
    public String getLineCommentPrefix() {
        return "--";
    }
}

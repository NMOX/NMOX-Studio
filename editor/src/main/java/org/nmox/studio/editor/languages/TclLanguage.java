package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Tcl as a first-class CSL language: the platform supplies the
 * editor kit, bracket logic and folding hooks; the TextMate grammar
 * (registered for this MIME) supplies the tokens.
 */
@LanguageRegistration(mimeType = "text/x-tcl")
public class TclLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Language.find("text/x-tcl");
    }

    @Override
    public String getDisplayName() {
        return "Tcl";
    }

    @Override
    public String getLineCommentPrefix() {
        return "#";
    }
}

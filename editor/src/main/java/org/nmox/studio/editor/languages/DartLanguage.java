package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Dart as a first-class CSL language: the platform supplies the
 * editor kit, comment toggling, bracket logic and folding hooks; the
 * TextMate grammar (registered for this MIME) supplies the tokens.
 */
@LanguageRegistration(mimeType = "text/x-dart")
public class DartLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Lexers.find("text/x-dart");
    }

    @Override
    public String getDisplayName() {
        return "Dart";
    }

    @Override
    public String getLineCommentPrefix() {
        return "//";
    }
}

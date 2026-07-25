package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/** Tact (TON contracts) as a first-class CSL language. */
@LanguageRegistration(mimeType = "text/x-tact")
public class TactLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Lexers.find("text/x-tact");
    }

    @Override
    public String getDisplayName() {
        return "Tact";
    }

    @Override
    public String getLineCommentPrefix() {
        return "//";
    }
}

package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/** Aiken (Cardano validators) as a first-class CSL language. */
@LanguageRegistration(mimeType = "text/x-aiken")
public class AikenLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Lexers.find("text/x-aiken");
    }

    @Override
    public String getDisplayName() {
        return "Aiken";
    }

    @Override
    public String getLineCommentPrefix() {
        return "//";
    }
}

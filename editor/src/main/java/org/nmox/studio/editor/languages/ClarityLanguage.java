package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/** Clarity (Stacks contracts) as a first-class CSL language. */
@LanguageRegistration(mimeType = "text/x-clarity")
public class ClarityLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Lexers.find("text/x-clarity");
    }

    @Override
    public String getDisplayName() {
        return "Clarity";
    }

    @Override
    public String getLineCommentPrefix() {
        return ";;";
    }
}

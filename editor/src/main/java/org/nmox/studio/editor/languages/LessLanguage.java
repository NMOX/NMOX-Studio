package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Less as a first-class CSL language; tokens come from the TextMate
 * grammar registered for this MIME.
 */
@LanguageRegistration(mimeType = "text/x-less")
public class LessLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Language.find("text/x-less");
    }

    @Override
    public String getDisplayName() {
        return "Less";
    }

    @Override
    public String getLineCommentPrefix() {
        return "//";
    }
}

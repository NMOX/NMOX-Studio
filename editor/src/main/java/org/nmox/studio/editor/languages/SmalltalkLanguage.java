package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Smalltalk as a first-class CSL language: the platform supplies the
 * editor kit, bracket logic and folding hooks; the TextMate grammar
 * (registered for this MIME) supplies the tokens.
 */
@LanguageRegistration(mimeType = "text/x-smalltalk")
public class SmalltalkLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Language.find("text/x-smalltalk");
    }

    @Override
    public String getDisplayName() {
        return "Smalltalk";
    }

    // Smalltalk has no line comment — only "..." block comments; the
    // platform's comment-toggle stays disabled rather than lying.
}

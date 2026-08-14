package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Aiken (Cardano smart-contract validators) as a first-class CSL
 * language. The one-line pattern to learn from this file: a TextMate
 * grammar (in {@code editor.grammars}) makes a MIME type HIGHLIGHT,
 * but only a CSL registration like this one gives it an editor kit —
 * comment toggling, brace pairs, keyword completion. Without it the
 * platform falls back to the plain-text kit and every MimeLookup
 * feature silently dies (the v1.217.0 lesson). The lexer resolves via
 * {@code Lexers.find}, never bare {@code Language.find} — see the
 * package doc for why that is a build law.
 */
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

package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * WIT (WebAssembly Interface Types) as a first-class CSL language: the
 * platform supplies the editor kit, comment toggling, bracket logic and
 * folding; the vendored TextMate grammar supplies the tokens. A grammar
 * alone does not make a mime (the v1.217.0 lesson) — this config is
 * what turns .wit files from plain text into an editor citizen.
 */
@LanguageRegistration(mimeType = "text/x-wit")
public class WitLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Lexers.find("text/x-wit");
    }

    @Override
    public String getDisplayName() {
        return "WIT";
    }

    @Override
    public String getLineCommentPrefix() {
        return "//";
    }
}

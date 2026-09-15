package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Vyper (Pythonic EVM smart contracts) as a first-class CSL language:
 * the platform supplies the editor kit, comment toggling and bracket
 * logic; the TextMate grammar supplies the tokens. Without this kit the
 * mime would open in the plain kit and every MimeLookup feature on it
 * (completion, typing, the outline) would be silently dead.
 *
 * <p>Comments are {@code #} lines; docstrings are triple-quoted strings
 * (NatSpec lives in them), which the grammar colours as strings and the
 * outline treats as prose.
 */
@LanguageRegistration(mimeType = "text/x-vyper")
public class VyperLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Lexers.find("text/x-vyper");
    }

    @Override
    public String getDisplayName() {
        return "Vyper";
    }

    @Override
    public String getLineCommentPrefix() {
        return "#";
    }
}

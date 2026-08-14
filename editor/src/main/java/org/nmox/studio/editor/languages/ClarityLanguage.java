package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Clarity (Stacks smart contracts) as a first-class CSL language.
 * Same shape as every sibling in this package — grammar makes it
 * highlight, THIS class makes it editable (comments {@code ;;}, brace
 * pairs, keywords). Clarity is a Lisp: its structure is parentheses
 * all the way down, which is why it deliberately has NO brace-family
 * Navigator outline while Aiken and Tact do (recorded when the trio
 * shipped in v1.155.0). Lexer via {@code Lexers.find} per the package
 * law.
 */
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

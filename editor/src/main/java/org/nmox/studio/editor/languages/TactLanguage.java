package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Tact (TON smart contracts) as a first-class CSL language — grammar
 * highlights, this class supplies the editor kit (comments, brace
 * pairs, keywords). Tact carries the TON chain here because FunC's
 * only grammar is GPL-licensed and archived (recorded in NOTICE when
 * v1.153.0 chose the MIT route). Lexer via {@code Lexers.find} per
 * the package law.
 */
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

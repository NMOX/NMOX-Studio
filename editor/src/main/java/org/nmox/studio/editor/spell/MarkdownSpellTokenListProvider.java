package org.nmox.studio.editor.spell;

import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.api.lexer.Token;
import org.netbeans.modules.spellchecker.spi.language.TokenList;
import org.netbeans.modules.spellchecker.spi.language.TokenListProvider;

/**
 * Spellcheck for markdown the way it should read: prose is checked, code
 * is not. Without this the platform treats the whole document as prose,
 * so every identifier inside a fenced block — const, useState, npm — is
 * flagged as a typo, and so is every URL.
 *
 * A token is prose unless its TextMate scope stack says otherwise:
 * fenced and indented code blocks (markup.fenced_code / markup.raw,
 * which also covers YAML front matter), inline code spans
 * (markup.inline.raw), link destinations (markup.underline.link), and
 * any embedded language (source.*) are all excluded.
 */
@MimeRegistrations({
    // position 100: must sort ahead of the platform markdown module's
    // PlainTokenListProvider (registered without a position), because the
    // spellchecker takes the first provider that returns a token list
    @MimeRegistration(mimeType = "text/x-markdown", service = TokenListProvider.class, position = 100),
    @MimeRegistration(mimeType = "text/markdown", service = TokenListProvider.class, position = 100)
})
public class MarkdownSpellTokenListProvider implements TokenListProvider {

    @Override
    public TokenList findTokenList(Document doc) {
        return new CodeSpellTokenListProvider.FilteredWordsTokenList(
                doc, MarkdownSpellTokenListProvider::isProse);
    }

    private static boolean isProse(Token<?> token) {
        return isProseScope(token.getProperty("categories"));
    }

    /**
     * Whether a markdown token is prose the spellchecker should read.
     * {@code categoriesProperty} is the TextMate scope stack (a List
     * whose toString looks like "[text.html.markdown,
     * markup.fenced_code.block.markdown, source.js]"); a null stack is
     * plain paragraph text, which is prose.
     */
    static boolean isProseScope(Object categoriesProperty) {
        if (categoriesProperty == null) {
            return true;
        }
        String stack = categoriesProperty.toString();
        return !(stack.contains("markup.fenced_code")
                || stack.contains("markup.raw")
                || stack.contains("markup.inline.raw")
                || stack.contains("markup.underline.link")
                || stack.contains("source."));
    }
}

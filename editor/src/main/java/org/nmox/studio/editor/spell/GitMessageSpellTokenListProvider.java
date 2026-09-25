package org.nmox.studio.editor.spell;

import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.lexer.Token;
import org.netbeans.modules.spellchecker.spi.language.TokenList;
import org.netbeans.modules.spellchecker.spi.language.TokenListProvider;

/**
 * Spellcheck for a commit message the way it reads (3.2.0): the message
 * is prose and is checked, and nothing else is. A commit message is the
 * one file git hands its editor that a person writes as sentences, so it
 * is the opposite of a code file — {@link CodeSpellTokenListProvider}
 * checks only comments, and here the comments are the one part NOT to
 * check: they are git's own template, and a {@code git commit -v} diff
 * below them is code.
 *
 * <p>The grammar (VS Code's git-commit) wraps every line the user writes
 * in {@code meta.scope.message.git-commit}; its {@code #} template lines
 * sit in {@code meta.scope.metadata} and the verbose diff in
 * {@code meta.embedded.diff}. So prose is exactly the message scope,
 * minus anything embedded.
 */
@MimeRegistration(mimeType = "text/x-git-commit", service = TokenListProvider.class)
public class GitMessageSpellTokenListProvider implements TokenListProvider {

    @Override
    public TokenList findTokenList(Document doc) {
        return new CodeSpellTokenListProvider.FilteredWordsTokenList(
                doc, GitMessageSpellTokenListProvider::isMessage);
    }

    private static boolean isMessage(Token<?> token) {
        return isMessageScope(token.getProperty("categories"));
    }

    /**
     * Whether a token of a git message file is the message a person wrote.
     * {@code categoriesProperty} is the TextMate scope stack (a List whose
     * toString names every scope from the root down); a missing stack
     * means the grammar never ran, and then nothing is claimed as prose —
     * an unlexed file is not evidence of a sentence.
     */
    static boolean isMessageScope(Object categoriesProperty) {
        if (categoriesProperty == null) {
            return false;
        }
        String stack = categoriesProperty.toString();
        return stack.contains("meta.scope.message")
                && !stack.contains("meta.embedded")
                && !stack.contains("comment.");
    }
}

package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;
import org.nmox.studio.editor.grammars.GitRebaseGrammar;

/**
 * {@code git rebase -i}'s todo list as a CSL language (3.2.0): the
 * vendored grammar reaches the screen, and Toggle Comment speaks
 * {@code #}, which in a todo list is how a line is dropped from the
 * rebase without deleting it.
 */
@LanguageRegistration(mimeType = GitRebaseGrammar.MIME)
public class GitRebaseLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Lexers.find(GitRebaseGrammar.MIME);
    }

    @Override
    public String getDisplayName() {
        return GitRebaseGrammar.displayName();
    }

    @Override
    public String getLineCommentPrefix() {
        return "#";
    }
}

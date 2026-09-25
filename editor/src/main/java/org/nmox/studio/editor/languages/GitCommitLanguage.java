package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;
import org.nmox.studio.editor.grammars.GitCommitGrammar;

/**
 * git's message files ({@code COMMIT_EDITMSG} and its siblings) as a CSL
 * language (3.2.0). Without a kit the mime would open in the plain editor
 * and the vendored grammar would never reach the screen (the v1.217.0
 * law); with it, git's {@code #} instructions paint as comments and
 * Toggle Comment speaks {@code #} — the character git itself strips.
 */
@LanguageRegistration(mimeType = GitCommitGrammar.MIME)
public class GitCommitLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Lexers.find(GitCommitGrammar.MIME);
    }

    @Override
    public String getDisplayName() {
        return GitCommitGrammar.displayName();
    }

    @Override
    public String getLineCommentPrefix() {
        return "#";
    }
}

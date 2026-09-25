package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.util.NbBundle;

/**
 * {@code git rebase -i}'s todo list, {@code git-rebase-todo}, as
 * {@code text/x-git-rebase} (3.2.0). The grammar is VS Code's own (see
 * NOTICE-grammars.md): {@code pick}/{@code reword}/{@code squash}/…
 * are commands, the abbreviated hash a constant, {@code #} lines
 * comments, and an {@code exec} line's command is handed to the shell
 * grammar this product already vendors.
 *
 * <p>Resolved by NAME in {@link ConfigFileResolver} — the file has no
 * extension — and made a real editor mime by {@code GitRebaseLanguage}.
 */
@GrammarRegistration(grammar = "gitrebase.tmLanguage.json", mimeType = GitRebaseGrammar.MIME)
@NbBundle.Messages("LBL_GitRebaseGrammar=Git Rebase Todo")
public final class GitRebaseGrammar {

    /** The mime {@code git-rebase-todo} resolves to. */
    public static final String MIME = "text/x-git-rebase";

    private GitRebaseGrammar() {
    }

    /** The mime's name, in the reader's language. */
    public static String displayName() {
        return Bundle.LBL_GitRebaseGrammar();
    }
}

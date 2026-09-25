package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.util.NbBundle;

/**
 * The message files git hands its editor (3.2.0): {@code COMMIT_EDITMSG},
 * {@code MERGE_MSG}, {@code TAG_EDITMSG}, {@code SQUASH_MSG},
 * {@code NOTES_EDITMSG} and {@code EDIT_DESCRIPTION}, all
 * {@code text/x-git-commit}.
 *
 * <p>3.2.0 makes the IDE git's editor ({@code git config core.editor
 * "nmox -w"}), and the first real commit opened as PLAIN TEXT: git's
 * {@code # Please enter the commit message…} lines read like the
 * message itself. The grammar is VS Code's own (see
 * NOTICE-grammars.md) and scopes those lines as comments, the summary
 * line as the message, and a {@code git commit -v} diff as an embedded
 * {@code source.diff} — which this product resolves to an empty stub,
 * so the diff reads as plain text rather than pruning the rule.
 *
 * <p>These files carry no extension, so the mime is resolved by NAME in
 * {@link ConfigFileResolver}. The CSL language
 * ({@code GitCommitLanguage}) is what makes the mime real: a grammar
 * alone gives the plain kit and every editor feature silently dies
 * (the v1.217.0 law).
 */
@GrammarRegistration(grammar = "gitcommit.tmLanguage.json", mimeType = GitCommitGrammar.MIME)
@NbBundle.Messages("LBL_GitCommitGrammar=Git Commit Message")
public final class GitCommitGrammar {

    /** The mime every git message file resolves to. */
    public static final String MIME = "text/x-git-commit";

    private GitCommitGrammar() {
    }

    /** The mime's name, in the reader's language. */
    public static String displayName() {
        return Bundle.LBL_GitCommitGrammar();
    }
}

package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Graphql TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "graphql.tmLanguage.json", mimeType = "text/x-graphql")
@MIMEResolver.ExtensionRegistration(displayName = "GraphQL", mimeType = "text/x-graphql", extension = {"graphql", "gql"})
public final class GraphqlGrammar {

    private GraphqlGrammar() {
    }
}

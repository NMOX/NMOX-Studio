package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Prisma TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "prisma.tmLanguage.json", mimeType = "text/x-prisma")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_PrismaGrammar_LOADER", mimeType = "text/x-prisma", extension = {"prisma"}, position = 2300)
@org.openide.util.NbBundle.Messages("LBL_PrismaGrammar_LOADER=Prisma")
public final class PrismaGrammar {

    private PrismaGrammar() {
    }
}

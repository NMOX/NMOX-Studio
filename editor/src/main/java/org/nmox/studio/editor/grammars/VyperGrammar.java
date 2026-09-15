package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Vyper TextMate grammar (see NOTICE-grammars.md for
 * provenance) for contracts ({@code .vy}) and interface files
 * ({@code .vyi}). Ledger 12 had held Vyper back because grammar-only
 * support would mislead without a toolchain behind it; Foundry now
 * compiles {@code .vy} files with {@code vyper} on PATH and Contract
 * Studio reads Foundry's {@code out/}, so the editor half is honest.
 */
@GrammarRegistration(grammar = "vyper.tmLanguage.json", mimeType = "text/x-vyper")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_VyperGrammar_LOADER", mimeType = "text/x-vyper", extension = {"vy", "vyi"}, position = 2459)
@org.openide.util.NbBundle.Messages("LBL_VyperGrammar_LOADER=Vyper")
public final class VyperGrammar {

    private VyperGrammar() {
    }
}

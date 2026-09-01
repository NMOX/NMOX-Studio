package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the WIT (WebAssembly Interface Types) TextMate grammar —
 * the Component Model's IDL, the futures-2031 bet that .wit files
 * become the polyglot web's shared contract format the way .proto did
 * for RPC (see docs/engineering/futures-2031.md). Provenance in
 * NOTICE-grammars.md: bytecodealliance/vscode-wit, Apache-2.0,
 * sha256-pinned.
 */
@GrammarRegistration(grammar = "wit.tmLanguage.json", mimeType = "text/x-wit")
@MIMEResolver.ExtensionRegistration(displayName = "WIT", mimeType = "text/x-wit", extension = {"wit"}, position = 2458)
public final class WitGrammar {

    private WitGrammar() {
    }
}

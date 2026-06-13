package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Proto TextMate grammar (see NOTICE-grammars.md for the
 * pinned upstream) and its file extensions. The platform's
 * textmate-lexer module does the tokenizing and theme mapping.
 */
@GrammarRegistration(grammar = "proto.tmLanguage.json", mimeType = "text/x-protobuf")
@MIMEResolver.ExtensionRegistration(displayName = "Protocol Buffers", mimeType = "text/x-protobuf", extension = {"proto"})
public final class ProtoGrammar {

    private ProtoGrammar() {
    }
}

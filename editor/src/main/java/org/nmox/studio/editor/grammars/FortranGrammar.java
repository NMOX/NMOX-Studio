package org.nmox.studio.editor.grammars;

import org.netbeans.modules.textmate.lexer.api.GrammarRegistration;
import org.openide.filesystems.MIMEResolver;

/**
 * Registers the Fortran (free-form) TextMate grammar (see
 * NOTICE-grammars.md for provenance) and its file extensions. Only the
 * free-form extensions are claimed — the grammar is source.fortran.free,
 * so fixed-form .f/.for (Fortran 77) are deliberately left unclaimed.
 */
@GrammarRegistration(grammar = "fortran.tmLanguage.json", mimeType = "text/x-fortran")
@MIMEResolver.ExtensionRegistration(displayName = "Fortran", mimeType = "text/x-fortran", extension = {"f90", "f95", "f03", "f08", "f18"}, position = 2439)
public final class FortranGrammar {

    private FortranGrammar() {
    }
}

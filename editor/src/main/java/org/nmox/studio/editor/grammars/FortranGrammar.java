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
// The capital spellings too: by Fortran convention a capital F means "run
// the preprocessor first", and the platform matches by case off Windows.
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_FortranGrammar_LOADER", mimeType = "text/x-fortran", extension = {"f90", "f95", "f03", "f08", "f18", "F90", "F95", "F03", "F08"}, position = 2439)
@org.openide.util.NbBundle.Messages("LBL_FortranGrammar_LOADER=Fortran")
public final class FortranGrammar {

    private FortranGrammar() {
    }
}

package org.nmox.studio.editor.languages;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.CommentHandler;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Angular templates ({@code .component.html}, text/x-ng-template) as a
 * CSL language (v1.217.0).
 *
 * <p><b>Why this class exists.</b> The mime's TextMate grammar alone is
 * not enough: a mime with only a grammar registration has no DataObject
 * loader and no EditorKit, so the default loader opens its files with
 * the PLAIN kit and every MimeLookup feature — the grammar's coloring,
 * completion, the comment toggle — silently never engages. This was
 * measured live: the v1.217.0 fixture rendered stone-white with three
 * independent mime consumers dead at once. The CSL registration is what
 * brings the loader ({@code GsfDataLoader}) and kit that make the mime
 * REAL; {@code getLexerLanguage} then hands tokenization to the
 * TextMate lexer, where the injected Angular grammars do the actual
 * work (see {@code NgTemplateGrammars}).
 */
@LanguageRegistration(mimeType = "text/x-ng-template")
public class NgTemplateLanguage extends DefaultLanguageConfig {

    @Override
    public Language<?> getLexerLanguage() {
        return Lexers.find("text/x-ng-template");
    }

    @Override
    public String getDisplayName() {
        return "Angular Template";
    }

    // no getLineCommentPrefix: HTML has only block comments. The CSL
    // kit installs its OWN toggle-comment action which SHADOWS any
    // same-named Actions-folder registration (measured live: the menu
    // item no-op'd), so the toggle must be configured CSL's way — a
    // CommentHandler carrying the block delimiters.
    @Override
    public CommentHandler getCommentHandler() {
        return new CommentHandler.DefaultCommentHandler() {
            @Override
            public String getCommentStartDelimiter() {
                return "<!--";
            }

            @Override
            public String getCommentEndDelimiter() {
                return "-->";
            }
        };
    }
}

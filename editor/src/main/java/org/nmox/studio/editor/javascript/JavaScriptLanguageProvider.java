package org.nmox.studio.editor.javascript;

import org.netbeans.api.lexer.InputAttributes;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.LanguagePath;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.LanguageEmbedding;
import org.netbeans.spi.lexer.LanguageProvider;
import org.openide.util.lookup.ServiceProvider;

/**
 * Language provider for JavaScript syntax highlighting.
 * Supplies the JavaScript lexer language to the platform's
 * lexer infrastructure for the text/javascript MIME type.
 */
@ServiceProvider(service = LanguageProvider.class)
public class JavaScriptLanguageProvider extends LanguageProvider {

    public static final String MIME_TYPE = "text/javascript";

    @Override
    public Language<?> findLanguage(String mimeType) {
        if (MIME_TYPE.equals(mimeType)) {
            return JavaScriptTokenId.language();
        }
        return null;
    }

    @Override
    public LanguageEmbedding<?> findLanguageEmbedding(Token<?> token,
            LanguagePath languagePath, InputAttributes inputAttributes) {
        return null;
    }
}

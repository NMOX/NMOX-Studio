package org.nmox.studio.editor.javascript;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;

/**
 * Language provider for JavaScript syntax highlighting.
 * Registers the JavaScript language with NetBeans.
 */
@LanguageRegistration(mimeType = "text/javascript")
public class JavaScriptLanguageProvider extends DefaultLanguageConfig {
    
    @Override
    public Language<JavaScriptTokenId> getLexerLanguage() {
        return JavaScriptTokenId.language();
    }
    
    @Override
    public String getDisplayName() {
        return "JavaScript";
    }
}
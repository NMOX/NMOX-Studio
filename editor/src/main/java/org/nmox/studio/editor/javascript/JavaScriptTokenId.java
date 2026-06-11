package org.nmox.studio.editor.javascript;

import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.TokenId;

/**
 * Token IDs for JavaScript lexical analysis.
 * Defines all the different types of tokens that can appear in JavaScript code.
 */
public enum JavaScriptTokenId implements TokenId {
    
    // Keywords
    KEYWORD("keyword"),
    
    // Literals
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    NULL("null"),
    UNDEFINED("undefined"),
    REGEX("regex"),
    
    // Identifiers and operators
    IDENTIFIER("identifier"),
    OPERATOR("operator"),
    DELIMITER("delimiter"),
    
    // Comments
    LINE_COMMENT("comment"),
    BLOCK_COMMENT("comment"),
    
    // Whitespace and errors
    WHITESPACE("whitespace"),
    ERROR("error"),
    
    // Template literals (ES6)
    TEMPLATE_STRING("template"),
    TEMPLATE_EXPRESSION("template-expression");
    
    private final String category;
    
    JavaScriptTokenId(String category) {
        this.category = category;
    }
    
    @Override
    public String primaryCategory() {
        return category;
    }
    
    /**
     * Lazy holder: building the Language touches JavaScriptLanguageHierarchy,
     * which references this enum's constants. Initializing it eagerly here
     * creates a static-init cycle that blows up with "Ids cannot be null"
     * whenever the hierarchy class happens to load first (order depends on
     * which test or caller runs first). The holder defers construction until
     * language() is called, after both classes are fully initialized.
     */
    private static final class LanguageHolder {
        static final Language<JavaScriptTokenId> LANGUAGE =
                new JavaScriptLanguageHierarchy().language();
    }

    public static Language<JavaScriptTokenId> language() {
        return LanguageHolder.LANGUAGE;
    }
}
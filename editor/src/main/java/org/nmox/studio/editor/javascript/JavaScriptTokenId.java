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
    
    private static final Language<JavaScriptTokenId> language = 
            new JavaScriptLanguageHierarchy().language();
    
    public static Language<JavaScriptTokenId> language() {
        return language;
    }
}
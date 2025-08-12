package org.nmox.studio.editor.javascript;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerRestartInfo;

/**
 * Language hierarchy for JavaScript lexical analysis.
 * Defines the structure and tokens for the JavaScript language.
 */
public class JavaScriptLanguageHierarchy extends LanguageHierarchy<JavaScriptTokenId> {
    
    private static final List<JavaScriptTokenId> tokens = Arrays.asList(new JavaScriptTokenId[] {
        JavaScriptTokenId.KEYWORD,
        JavaScriptTokenId.STRING,
        JavaScriptTokenId.NUMBER,
        JavaScriptTokenId.BOOLEAN,
        JavaScriptTokenId.NULL,
        JavaScriptTokenId.UNDEFINED,
        JavaScriptTokenId.REGEX,
        JavaScriptTokenId.IDENTIFIER,
        JavaScriptTokenId.OPERATOR,
        JavaScriptTokenId.DELIMITER,
        JavaScriptTokenId.LINE_COMMENT,
        JavaScriptTokenId.BLOCK_COMMENT,
        JavaScriptTokenId.WHITESPACE,
        JavaScriptTokenId.ERROR,
        JavaScriptTokenId.TEMPLATE_STRING,
        JavaScriptTokenId.TEMPLATE_EXPRESSION
    });
    
    private static final Map<String, JavaScriptTokenId> flyweights = new HashMap<>();
    
    static {
        // JavaScript keywords
        String[] keywords = {
            "abstract", "await", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "debugger", "default", "delete", "do", "double", "else", "enum", "export",
            "extends", "false", "final", "finally", "float", "for", "function", "goto", "if",
            "implements", "import", "in", "instanceof", "int", "interface", "let", "long", "native",
            "new", "null", "package", "private", "protected", "public", "return", "short", "static",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "true", "try",
            "typeof", "var", "void", "volatile", "while", "with", "yield", "async", "of"
        };
        
        for (String keyword : keywords) {
            flyweights.put(keyword, JavaScriptTokenId.KEYWORD);
        }
        
        // Boolean literals
        flyweights.put("true", JavaScriptTokenId.BOOLEAN);
        flyweights.put("false", JavaScriptTokenId.BOOLEAN);
        
        // Special values
        flyweights.put("null", JavaScriptTokenId.NULL);
        flyweights.put("undefined", JavaScriptTokenId.UNDEFINED);
    }
    
    @Override
    protected synchronized Lexer<JavaScriptTokenId> createLexer(LexerRestartInfo<JavaScriptTokenId> info) {
        return new JavaScriptLexer(info);
    }
    
    @Override
    protected synchronized Collection<JavaScriptTokenId> createTokenIds() {
        return tokens;
    }
    
    @Override
    protected synchronized Map<String, Collection<JavaScriptTokenId>> createTokenCategories() {
        Map<String, Collection<JavaScriptTokenId>> cats = new HashMap<>();
        return cats;
    }
    
    @Override
    protected synchronized String mimeType() {
        return "text/javascript";
    }
    
    /**
     * Get the token ID for a given text, useful for keyword recognition.
     */
    public static JavaScriptTokenId getToken(String text) {
        return flyweights.get(text);
    }
}
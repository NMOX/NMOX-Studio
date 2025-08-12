package org.nmox.studio.editor.javascript;

import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.netbeans.spi.lexer.TokenFactory;

/**
 * Lexer for JavaScript language.
 * Provides syntax highlighting by tokenizing JavaScript source code.
 */
public class JavaScriptLexer implements Lexer<JavaScriptTokenId> {
    
    private static final int EOF = LexerInput.EOF;
    
    private final LexerInput input;
    private final TokenFactory<JavaScriptTokenId> tokenFactory;
    
    public JavaScriptLexer(LexerRestartInfo<JavaScriptTokenId> info) {
        this.input = info.input();
        this.tokenFactory = info.tokenFactory();
    }
    
    @Override
    public Token<JavaScriptTokenId> nextToken() {
        while (true) {
            int ch = input.read();
            
            switch (ch) {
                case EOF:
                    return null;
                    
                case ' ':
                case '\t':
                case '\n':
                case '\r':
                    return finishWhitespace();
                    
                case '/':
                    switch (input.read()) {
                        case '/':
                            return finishLineComment();
                        case '*':
                            return finishBlockComment();
                        case '=':
                            return tokenFactory.createToken(JavaScriptTokenId.OPERATOR);
                        default:
                            input.backup(1);
                            return finishRegexOrOperator();
                    }
                    
                case '"':
                case '\'':
                    return finishStringLiteral(ch);
                    
                case '`':
                    return finishTemplateLiteral();
                    
                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    return finishNumberLiteral();
                    
                case '+': case '-': case '*': case '%':
                case '=': case '!': case '<': case '>':
                case '&': case '|': case '^': case '~':
                case '?': case ':':
                    return finishOperator();
                    
                case '(': case ')': case '[': case ']':
                case '{': case '}': case ';': case ',':
                case '.':
                    return tokenFactory.createToken(JavaScriptTokenId.DELIMITER);
                    
                default:
                    if (Character.isJavaIdentifierStart(ch)) {
                        return finishIdentifier();
                    } else {
                        return tokenFactory.createToken(JavaScriptTokenId.ERROR);
                    }
            }
        }
    }
    
    private Token<JavaScriptTokenId> finishWhitespace() {
        while (true) {
            int ch = input.read();
            if (ch == EOF || !Character.isWhitespace(ch)) {
                input.backup(1);
                break;
            }
        }
        return tokenFactory.createToken(JavaScriptTokenId.WHITESPACE);
    }
    
    private Token<JavaScriptTokenId> finishLineComment() {
        while (true) {
            int ch = input.read();
            if (ch == EOF || ch == '\n' || ch == '\r') {
                input.backup(1);
                break;
            }
        }
        return tokenFactory.createToken(JavaScriptTokenId.LINE_COMMENT);
    }
    
    private Token<JavaScriptTokenId> finishBlockComment() {
        while (true) {
            int ch = input.read();
            if (ch == EOF) {
                break;
            }
            if (ch == '*') {
                if (input.read() == '/') {
                    break;
                } else {
                    input.backup(1);
                }
            }
        }
        return tokenFactory.createToken(JavaScriptTokenId.BLOCK_COMMENT);
    }
    
    private Token<JavaScriptTokenId> finishStringLiteral(int quote) {
        while (true) {
            int ch = input.read();
            if (ch == EOF || ch == '\n' || ch == '\r') {
                input.backup(1);
                break;
            }
            if (ch == quote) {
                break;
            }
            if (ch == '\\') {
                ch = input.read(); // Skip escaped character
                if (ch == EOF || ch == '\n' || ch == '\r') {
                    input.backup(1);
                    break;
                }
            }
        }
        return tokenFactory.createToken(JavaScriptTokenId.STRING);
    }
    
    private Token<JavaScriptTokenId> finishTemplateLiteral() {
        while (true) {
            int ch = input.read();
            if (ch == EOF) {
                break;
            }
            if (ch == '`') {
                break;
            }
            if (ch == '\\') {
                ch = input.read(); // Skip escaped character
                if (ch == EOF) {
                    break;
                }
            }
            // Note: In a full implementation, we'd handle ${} expressions
        }
        return tokenFactory.createToken(JavaScriptTokenId.TEMPLATE_STRING);
    }
    
    private Token<JavaScriptTokenId> finishNumberLiteral() {
        boolean hasDecimalPoint = false;
        boolean hasExponent = false;
        
        while (true) {
            int ch = input.read();
            if (ch == EOF) {
                input.backup(1);
                break;
            }
            
            if (Character.isDigit(ch)) {
                continue;
            } else if (ch == '.' && !hasDecimalPoint && !hasExponent) {
                hasDecimalPoint = true;
            } else if ((ch == 'e' || ch == 'E') && !hasExponent) {
                hasExponent = true;
                ch = input.read();
                if (ch == '+' || ch == '-') {
                    // Continue with exponent
                } else {
                    input.backup(1);
                }
            } else {
                input.backup(1);
                break;
            }
        }
        return tokenFactory.createToken(JavaScriptTokenId.NUMBER);
    }
    
    private Token<JavaScriptTokenId> finishOperator() {
        // Handle multi-character operators
        int ch = input.read();
        if (ch == '=' || ch == '+' || ch == '-' || ch == '&' || ch == '|') {
            // Common two-character operators: ==, !=, <=, >=, ++, --, &&, ||, etc.
        } else {
            input.backup(1);
        }
        return tokenFactory.createToken(JavaScriptTokenId.OPERATOR);
    }
    
    private Token<JavaScriptTokenId> finishRegexOrOperator() {
        // This is simplified - in a full implementation, we'd need context
        // to determine if '/' starts a regex or is a division operator
        return tokenFactory.createToken(JavaScriptTokenId.OPERATOR);
    }
    
    private Token<JavaScriptTokenId> finishIdentifier() {
        while (true) {
            int ch = input.read();
            if (ch == EOF || !Character.isJavaIdentifierPart(ch)) {
                input.backup(1);
                break;
            }
        }
        
        // Check if this identifier is a keyword
        CharSequence text = input.readText();
        JavaScriptTokenId keywordId = JavaScriptLanguageHierarchy.getToken(text.toString());
        if (keywordId != null) {
            return tokenFactory.createToken(keywordId);
        }
        
        return tokenFactory.createToken(JavaScriptTokenId.IDENTIFIER);
    }
    
    @Override
    public Object state() {
        return null; // No state needed for this simple lexer
    }
    
    @Override
    public void release() {
        // Nothing to release
    }
}
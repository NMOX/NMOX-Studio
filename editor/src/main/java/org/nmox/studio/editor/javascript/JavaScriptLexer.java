package org.nmox.studio.editor.javascript;

import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.netbeans.spi.lexer.TokenFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Lexer for JavaScript language.
 * Provides syntax highlighting by tokenizing JavaScript source code.
 * Optimized for performance with caching and efficient character processing.
 */
public class JavaScriptLexer implements Lexer<JavaScriptTokenId> {
    
    private static final int EOF = LexerInput.EOF;
    
    private static final Map<String, JavaScriptTokenId> KEYWORD_CACHE = new HashMap<>();
    static {
        for (JavaScriptTokenId tokenId : JavaScriptTokenId.values()) {
            if (tokenId.primaryCategory().startsWith("keyword")) {
                KEYWORD_CACHE.put(tokenId.name().toLowerCase(), tokenId);
            }
        }
    }
    
    private final LexerInput input;
    private final TokenFactory<JavaScriptTokenId> tokenFactory;
    private final StringBuilder buffer = new StringBuilder(128);
    private LexerState state = LexerState.NORMAL;
    /**
     * Whether a '/' at this point starts a regex literal (true after
     * operators, '(', '=', 'return' …) or is division (false after a
     * value: identifier, number, string, ')', ']'). The classic JS
     * lexing ambiguity, resolved the way every practical lexer does -
     * by what the previous meaningful token was.
     */
    private boolean regexAllowed = true;

    private enum LexerState {
        NORMAL,
        IN_TEMPLATE_LITERAL,
        IN_TEMPLATE_EXPRESSION
    }

    public JavaScriptLexer(LexerRestartInfo<JavaScriptTokenId> info) {
        this.input = info.input();
        this.tokenFactory = info.tokenFactory();
        if (info.state() instanceof Integer packed) {
            this.state = LexerState.values()[packed >> 1];
            this.regexAllowed = (packed & 1) != 0;
        }
    }
    
    @Override
    public Token<JavaScriptTokenId> nextToken() {
        while (true) {
            int ch = input.read();
            
            if (ch == EOF) {
                return null;
            }
            
            if (state == LexerState.IN_TEMPLATE_LITERAL) {
                return continueTemplateLiteral(ch);
            }
            
            if (ch <= ' ') {
                if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                    return finishWhitespace();
                }
            }
            
            switch (ch) {
                case '/':
                    int next = input.read();
                    if (next == '/') {
                        return finishLineComment();
                    } else if (next == '*') {
                        return finishBlockComment();
                    } else if (next == '=' && !regexAllowed) {
                        regexAllowed = true;
                        return tokenFactory.createToken(JavaScriptTokenId.OPERATOR);
                    } else {
                        input.backup(1);
                        return finishRegexOrOperator();
                    }

                case '"':
                case '\'':
                    regexAllowed = false;
                    return finishStringLiteral(ch);

                case '`':
                    state = LexerState.IN_TEMPLATE_LITERAL;
                    regexAllowed = false;
                    return finishTemplateLiteral();

                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    regexAllowed = false;
                    return finishNumberLiteral();

                case '+': case '-': case '*': case '%':
                case '=': case '!': case '<': case '>':
                case '&': case '|': case '^': case '~':
                case '?': case ':':
                    regexAllowed = true;
                    return finishOperator();

                case '(': case ')': case '[': case ']':
                case '{': case '}': case ';': case ',':
                case '.':
                    // after a closing paren/bracket a '/' divides; after
                    // the openers and separators it starts a regex
                    regexAllowed = ch != ')' && ch != ']';
                    return tokenFactory.createToken(JavaScriptTokenId.DELIMITER);

                default:
                    if (Character.isJavaIdentifierStart(ch)) {
                        return finishIdentifier();
                    } else {
                        regexAllowed = true;
                        return tokenFactory.createToken(JavaScriptTokenId.ERROR);
                    }
            }
        }
    }
    
    private Token<JavaScriptTokenId> finishWhitespace() {
        int ch;
        while ((ch = input.read()) != EOF && ch <= ' ' && 
               (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r')) {
        }
        if (ch != EOF) {
            input.backup(1);
        }
        return tokenFactory.createToken(JavaScriptTokenId.WHITESPACE);
    }
    
    private Token<JavaScriptTokenId> finishLineComment() {
        int ch;
        while ((ch = input.read()) != EOF && ch != '\n' && ch != '\r') {
        }
        if (ch != EOF) {
            input.backup(1);
        }
        return tokenFactory.createToken(JavaScriptTokenId.LINE_COMMENT);
    }
    
    private Token<JavaScriptTokenId> finishBlockComment() {
        int ch;
        while ((ch = input.read()) != EOF) {
            if (ch == '*') {
                int next = input.read();
                if (next == '/') {
                    break;
                } else if (next != EOF) {
                    input.backup(1);
                }
            }
        }
        return tokenFactory.createToken(JavaScriptTokenId.BLOCK_COMMENT);
    }
    
    private Token<JavaScriptTokenId> finishStringLiteral(int quote) {
        int ch;
        while ((ch = input.read()) != EOF) {
            if (ch == quote) {
                break;
            }
            if (ch == '\n' || ch == '\r') {
                input.backup(1);
                break;
            }
            if (ch == '\\') {
                int next = input.read();
                if (next == EOF || next == '\n' || next == '\r') {
                    if (next != EOF) {
                        input.backup(1);
                    }
                    break;
                }
            }
        }
        return tokenFactory.createToken(JavaScriptTokenId.STRING);
    }
    
    private Token<JavaScriptTokenId> finishTemplateLiteral() {
        int ch;
        int depth = 0;
        while ((ch = input.read()) != EOF) {
            if (ch == '`' && depth == 0) {
                state = LexerState.NORMAL;
                break;
            }
            if (ch == '\\') {
                int next = input.read();
                if (next == EOF) {
                    break;
                }
            } else if (ch == '$') {
                int next = input.read();
                if (next == '{') {
                    depth++;
                } else if (next != EOF) {
                    input.backup(1);
                }
            } else if (ch == '}' && depth > 0) {
                depth--;
            }
        }
        return tokenFactory.createToken(JavaScriptTokenId.TEMPLATE_STRING);
    }
    
    private Token<JavaScriptTokenId> continueTemplateLiteral(int ch) {
        if (ch == '`') {
            state = LexerState.NORMAL;
            return tokenFactory.createToken(JavaScriptTokenId.DELIMITER);
        }
        return finishTemplateLiteral();
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
        if (!regexAllowed) {
            // division: value on the left
            regexAllowed = true;
            return tokenFactory.createToken(JavaScriptTokenId.OPERATOR);
        }
        // try to scan a regex literal; on any malformation back out and
        // call it an operator so highlighting degrades gracefully
        int consumed = 0;
        boolean inClass = false;
        boolean escaped = false;
        while (true) {
            int ch = input.read();
            if (ch == EOF || ch == '\n' || ch == '\r') {
                if (ch != EOF) {
                    consumed++;
                }
                input.backup(consumed);
                regexAllowed = true;
                return tokenFactory.createToken(JavaScriptTokenId.OPERATOR);
            }
            consumed++;
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '[') {
                inClass = true;
            } else if (ch == ']') {
                inClass = false;
            } else if (ch == '/' && !inClass) {
                break; // closed
            }
        }
        // trailing flags: /pattern/gimsuy
        int ch;
        while ((ch = input.read()) != EOF && Character.isLetter(ch)) {
        }
        if (ch != EOF) {
            input.backup(1);
        }
        regexAllowed = false;
        return tokenFactory.createToken(JavaScriptTokenId.REGEX);
    }
    
    private Token<JavaScriptTokenId> finishIdentifier() {
        buffer.setLength(0);
        int ch = input.read();
        
        if (ch != EOF) {
            input.backup(1);
        }
        
        while ((ch = input.read()) != EOF && Character.isJavaIdentifierPart(ch)) {
            buffer.append((char) ch);
        }
        
        if (ch != EOF) {
            input.backup(1);
        }
        
        CharSequence text = input.readText();
        String str = text.toString();
        
        JavaScriptTokenId keywordId = KEYWORD_CACHE.get(str);
        if (keywordId == null) {
            keywordId = JavaScriptLanguageHierarchy.getToken(str);
        }
        
        if (keywordId != null) {
            // after most keywords (return, typeof, case …) a '/' starts a
            // regex; 'this' and 'super' are values, so it divides
            regexAllowed = !"this".equals(str) && !"super".equals(str);
            return tokenFactory.createToken(keywordId);
        }

        regexAllowed = false;
        return tokenFactory.createToken(JavaScriptTokenId.IDENTIFIER);
    }

    @Override
    public Object state() {
        return (state.ordinal() << 1) | (regexAllowed ? 1 : 0);
    }

    @Override
    public void release() {
        buffer.setLength(0);
        state = LexerState.NORMAL;
        regexAllowed = true;
    }
}
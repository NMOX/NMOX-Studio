package org.nmox.studio.editor.javascript;

import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.netbeans.spi.lexer.TokenFactory;
import java.util.Objects;

/**
 * Lexer for JavaScript language.
 * Provides syntax highlighting by tokenizing JavaScript source code.
 * Keywords resolve through JavaScriptLanguageHierarchy's flyweight map, which
 * is the one table; this class keeps none of its own.
 *
 * <p><b>Template literals</b> lex as three kinds of token, not one:
 * {@code TEMPLATE_STRING} for each literal run (the backticks ride along with
 * the text beside them), {@code TEMPLATE_EXPRESSION} for the {@code ${} and
 * {@code }} that delimit an interpolation, and ordinary JavaScript tokens for
 * what sits between them — so {@code `hi ${user.name}!`} colours its
 * identifiers as identifiers. Before v2.186.0 the whole literal collapsed
 * into one string token and {@code TEMPLATE_EXPRESSION}, which has had a
 * registered colour in {@code syntax-colors.xml} since the enum was written,
 * was emitted by nothing.
 */
public class JavaScriptLexer implements Lexer<JavaScriptTokenId> {

    private static final int EOF = LexerInput.EOF;

    private final LexerInput input;
    private final TokenFactory<JavaScriptTokenId> tokenFactory;
    private final StringBuilder buffer = new StringBuilder(128);
    /**
     * The innermost template literal being lexed, or null outside them all.
     * See {@link Frame}.
     */
    private Frame templates;
    /**
     * Whether a '/' at this point starts a regex literal (true after
     * operators, '(', '=', 'return' …) or is division (false after a
     * value: identifier, number, string, ')', ']'). The classic JS
     * lexing ambiguity, resolved the way every practical lexer does -
     * by what the previous meaningful token was.
     */
    private boolean regexAllowed = true;

    /**
     * One enclosing template literal, as a node of an immutable persistent
     * stack: {@link #outer} is the template this one sits inside through an
     * interpolation (null at the outermost), and {@link #braces} says WHERE
     * inside this template the lexer stands — {@link #TEXT} for the literal
     * run, or the count of unmatched '{' inside the interpolation currently
     * open, which is how the '}' that closes {@code ${…}} is told from the
     * one that closes an object literal inside it.
     *
     * <p>Persistent rather than a mutable stack because {@link #state()} must
     * hand the platform an immutable value it keeps and compares: pushing is
     * one new node and popping is {@code outer}, so nothing is copied per
     * token however deep the nesting goes.
     */
    private static final class Frame {
        static final int TEXT = -1;

        final int braces;
        final Frame outer;
        private final int hash;

        Frame(int braces, Frame outer) {
            this.braces = braces;
            this.outer = outer;
            this.hash = 31 * (outer == null ? 0 : outer.hash) + braces;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            return o instanceof Frame f && braces == f.braces
                    && Objects.equals(outer, f.outer);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /**
     * Everything an incremental relex must restart from. Immutable with value
     * equality, because the platform keeps it and compares it to decide where
     * relexing may stop.
     */
    private record State(Frame templates, boolean regexAllowed) { }

    /** The two states outside any template, cached: the overwhelming majority. */
    private static final State CODE_REGEX_ALLOWED = new State(null, true);
    private static final State CODE_AFTER_VALUE = new State(null, false);

    public JavaScriptLexer(LexerRestartInfo<JavaScriptTokenId> info) {
        this.input = info.input();
        this.tokenFactory = info.tokenFactory();
        if (info.state() instanceof State restart) {
            this.templates = restart.templates();
            this.regexAllowed = restart.regexAllowed();
        }
    }
    
    @Override
    public Token<JavaScriptTokenId> nextToken() {
        while (true) {
            int ch = input.read();
            
            if (ch == EOF) {
                return null;
            }
            
            if (inTemplateText()) {
                // the run is scanned from its own start, so give back the peek
                input.backup(1);
                Token<JavaScriptTokenId> text = scanTemplateText();
                if (text != null) {
                    return text;
                }
                // an empty run is the ONE thing scanTemplateText refuses to
                // tokenize, and it happens only when "${" sits right here
                input.read();
                input.read();
                openInterpolation();
                regexAllowed = true;    // an expression begins after "${"
                return tokenFactory.createToken(JavaScriptTokenId.TEMPLATE_EXPRESSION);
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
                    openTemplate();
                    // the backtick is already read, so the run is never empty
                    return scanTemplateText();

                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    regexAllowed = false;
                    return finishNumberLiteral();

                case '+': case '-': case '*': case '%':
                case '=': case '!': case '<': case '>':
                case '&': case '|': case '^': case '~':
                case '?': case ':':
                    regexAllowed = true;
                    return finishOperator(ch);

                case '{':
                    regexAllowed = true;
                    if (inInterpolation()) {
                        // an object literal or block inside "${ … }": its own
                        // closer must not be read as the end of the expression
                        setBraceDepth(templates.braces + 1);
                    }
                    return tokenFactory.createToken(JavaScriptTokenId.DELIMITER);

                case '}':
                    if (inInterpolation()) {
                        if (templates.braces == 0) {
                            closeInterpolation();
                            return tokenFactory.createToken(
                                    JavaScriptTokenId.TEMPLATE_EXPRESSION);
                        }
                        setBraceDepth(templates.braces - 1);
                    }
                    regexAllowed = true;
                    return tokenFactory.createToken(JavaScriptTokenId.DELIMITER);

                case '(': case ')': case '[': case ']':
                case ';': case ',':
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
    
    /**
     * One literal run of a template: everything from here up to AND INCLUDING
     * the closing backtick, or up to (not including) the {@code ${} that opens
     * an interpolation. A backslash consumes the character after it, so
     * {@code \${x}} stays literal text and {@code \`} does not close the
     * template. EOF ends the run where it falls — an unterminated template
     * never swallows the rest of the file and never loops.
     *
     * <p>Returns null when the run would be EMPTY, which the lexer API cannot
     * express: it happens only when {@code ${} sits at the very start, i.e.
     * directly after the opening backtick's own run or after a {@code }} that
     * just closed one. The caller emits the {@code ${} instead, so every call
     * of {@link #nextToken()} still consumes at least one character.
     */
    private Token<JavaScriptTokenId> scanTemplateText() {
        while (true) {
            int ch = input.read();
            if (ch == EOF) {
                break;
            }
            if (ch == '`') {
                closeTemplate();
                break;
            }
            if (ch == '\\') {
                if (input.read() == EOF) {
                    break;
                }
                continue;
            }
            if (ch == '$') {
                int next = input.read();
                if (next == '{') {
                    input.backup(2);          // "${" is the next token
                    break;
                }
                if (next != EOF) {
                    input.backup(1);          // a lone '$' is just text
                }
            }
        }
        return input.readLength() == 0 ? null
                : tokenFactory.createToken(JavaScriptTokenId.TEMPLATE_STRING);
    }

    /** In the literal run of a template — not in code, not outside one. */
    private boolean inTemplateText() {
        return templates != null && templates.braces == Frame.TEXT;
    }

    /** Inside a template's {@code ${ … }}, where JavaScript is lexed. */
    private boolean inInterpolation() {
        return templates != null && templates.braces >= 0;
    }

    private void openTemplate() {
        templates = new Frame(Frame.TEXT, templates);
    }

    private void closeTemplate() {
        templates = templates.outer;
        regexAllowed = false;                 // a template literal is a value
    }

    private void openInterpolation() {
        templates = new Frame(0, templates.outer);
    }

    private void closeInterpolation() {
        templates = new Frame(Frame.TEXT, templates.outer);
        regexAllowed = false;
    }

    private void setBraceDepth(int depth) {
        templates = new Frame(depth, templates.outer);
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
    
    private Token<JavaScriptTokenId> finishOperator(int first) {
        // Handle multi-character operators
        int ch = input.read();
        if (ch == '=' || ch == '+' || ch == '-' || ch == '&' || ch == '|') {
            // Common two-character operators: ==, !=, <=, >=, ++, --, &&, ||, etc.
            if ((first == '+' || first == '-') && ch == first) {
                // ++ / -- yields a value, so a following '/' is division,
                // not the start of a regex literal (e.g. `i++ / 2`)
                regexAllowed = false;
            }
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
        
        // ONE keyword table: the hierarchy's flyweight map. A second one used
        // to be consulted first — built by walking the token ids for
        // categories starting with "keyword", which matches exactly one
        // constant, so it was the single entry {"keyword" -> KEYWORD}. It
        // missed every real keyword and hit only the identifier `keyword`,
        // painting an ordinary variable name in keyword colour.
        JavaScriptTokenId keywordId = JavaScriptLanguageHierarchy.getToken(str);
        
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
        if (templates == null) {
            return regexAllowed ? CODE_REGEX_ALLOWED : CODE_AFTER_VALUE;
        }
        return new State(templates, regexAllowed);
    }

    @Override
    public void release() {
        buffer.setLength(0);
        templates = null;
        regexAllowed = true;
    }
}
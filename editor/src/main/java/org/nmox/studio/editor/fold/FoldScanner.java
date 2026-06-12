package org.nmox.studio.editor.fold;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.nmox.studio.editor.javascript.JavaScriptTokenId;

/**
 * Finds foldable regions by walking the lexer's token stream - so
 * braces inside strings, templates and comments never confuse it.
 * Pure function of (text, language); unit-testable without an editor.
 */
public final class FoldScanner {

    /** A foldable span of text. */
    public record Span(int start, int end, boolean comment) {
    }

    private FoldScanner() {
    }

    /**
     * Multi-line {...} blocks and multi-line block comments. The
     * returned spans cover the braces/comment markers themselves.
     */
    public static List<Span> scan(CharSequence text, Language<JavaScriptTokenId> language) {
        List<Span> spans = new ArrayList<>();
        TokenHierarchy<?> hierarchy = TokenHierarchy.create(text, language);
        TokenSequence<JavaScriptTokenId> ts = hierarchy.tokenSequence(language);
        if (ts == null) {
            return spans;
        }
        Deque<Integer> braceStack = new ArrayDeque<>();
        while (ts.moveNext()) {
            JavaScriptTokenId id = ts.token().id();
            String tokenText = ts.token().text().toString();
            int offset = ts.offset();
            if (id == JavaScriptTokenId.BLOCK_COMMENT) {
                if (spansLines(text, offset, offset + tokenText.length())) {
                    spans.add(new Span(offset, offset + tokenText.length(), true));
                }
            } else if (id == JavaScriptTokenId.DELIMITER || id == JavaScriptTokenId.OPERATOR) {
                // the lexer may emit braces as single-char delimiter/operator tokens
                for (int i = 0; i < tokenText.length(); i++) {
                    char c = tokenText.charAt(i);
                    if (c == '{') {
                        braceStack.push(offset + i);
                    } else if (c == '}' && !braceStack.isEmpty()) {
                        int start = braceStack.pop();
                        int end = offset + i + 1;
                        if (spansLines(text, start, end)) {
                            spans.add(new Span(start, end, false));
                        }
                    }
                }
            }
        }
        return spans;
    }

    private static boolean spansLines(CharSequence text, int start, int end) {
        for (int i = start; i < Math.min(end, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                return true;
            }
        }
        return false;
    }
}

package org.nmox.studio.editor.typing;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.spi.editor.typinghooks.TypedBreakInterceptor;

/**
 * Smart Enter: between "{" and "}" it opens an indented body with the
 * closing brace on its own line; after a lone "{" it indents one stop;
 * inside a block comment it continues the " * " gutter.
 */
public class JsTypedBreakInterceptor implements TypedBreakInterceptor {

    private static final String INDENT = "    ";

    @Override
    public boolean beforeInsert(Context context) {
        return false;
    }

    @Override
    public void insert(MutableContext context) throws BadLocationException {
        Document doc = context.getDocument();
        int offset = context.getCaretOffset();
        String lineIndent = lineIndentAt(doc, offset);

        char prev = offset > 0 ? doc.getText(offset - 1, 1).charAt(0) : 0;
        char next = offset < doc.getLength() ? doc.getText(offset, 1).charAt(0) : 0;
        String line = currentLine(doc, offset);

        Break br = computeBreak(prev, next, line, lineIndent);
        if (br != null) {
            context.setText(br.text(), 0, br.caret());
        }
    }

    /** The text inserted for a smart break and where the caret lands in it. */
    record Break(String text, int caret) {
    }

    /**
     * The smart-Enter decision, made purely from the character before and
     * after the caret, the current line, and its indent. Returns null when
     * there is nothing smart to do (plain newline at column 0). Package-
     * visible so the branch logic is testable without a MutableContext.
     */
    static Break computeBreak(char prev, char next, String line, String lineIndent) {
        if (prev == '{' && next == '}') {
            // caret lands on the indented empty body line
            String text = "\n" + lineIndent + INDENT + "\n" + lineIndent;
            return new Break(text, 1 + lineIndent.length() + INDENT.length());
        }
        if (prev == '{') {
            String text = "\n" + lineIndent + INDENT;
            return new Break(text, text.length());
        }
        String trimmed = line.trim();
        if ((trimmed.startsWith("/*") || trimmed.startsWith("*")) && !trimmed.contains("*/")) {
            String text = "\n" + lineIndent + (trimmed.startsWith("/*") ? " * " : "* ");
            return new Break(text, text.length());
        }
        if (!lineIndent.isEmpty()) {
            String text = "\n" + lineIndent;
            return new Break(text, text.length());
        }
        return null;
    }

    private static String currentLine(Document doc, int offset) throws BadLocationException {
        Element root = doc.getDefaultRootElement();
        Element line = root.getElement(root.getElementIndex(offset));
        return doc.getText(line.getStartOffset(),
                Math.min(offset, line.getEndOffset()) - line.getStartOffset());
    }

    private static String lineIndentAt(Document doc, int offset) throws BadLocationException {
        String line = currentLine(doc, offset);
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return line.substring(0, i);
    }

    @Override
    public void afterInsert(Context context) {
    }

    @Override
    public void cancelled(Context context) {
    }

    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/javascript", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/typescript", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-java", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-c", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-cpp", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-python", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-ruby", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-rust", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-php5", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/sh", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-go", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-json", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-erlang", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-elixir", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-clojure", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-lisp", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-lua", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-swift", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-kotlin", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-csharp", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-fsharp", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-groovy", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-perl", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-r", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-julia", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-dart", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-scala", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-haskell", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-zig", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-ocaml", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-crystal", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/css", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-scss", service = TypedBreakInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-less", service = TypedBreakInterceptor.Factory.class)
    })
    public static class Factory implements TypedBreakInterceptor.Factory {
        @Override
        public TypedBreakInterceptor createTypedBreakInterceptor(
                org.netbeans.api.editor.mimelookup.MimePath mimePath) {
            return new JsTypedBreakInterceptor();
        }
    }
}

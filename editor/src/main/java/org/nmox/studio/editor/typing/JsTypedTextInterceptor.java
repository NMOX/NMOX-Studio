package org.nmox.studio.editor.typing;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor;

/**
 * Bracket and quote intelligence while typing: openers auto-close,
 * closers and quotes type over their twins, so `(`, `[`, `{`, quotes
 * and backticks behave like every modern editor.
 */
public class JsTypedTextInterceptor implements TypedTextInterceptor {

    @Override
    public boolean beforeInsert(Context context) {
        return false;
    }

    @Override
    public void insert(MutableContext context) throws BadLocationException {
        String typedText = context.getText();
        if (typedText.length() != 1) {
            return;
        }
        char typed = typedText.charAt(0);
        Document doc = context.getDocument();
        int offset = context.getOffset();
        char next = offset < doc.getLength()
                ? doc.getText(offset, 1).charAt(0) : 0;

        if (PairLogic.shouldTypeOver(typed, next)) {
            // consume the keystroke, just move the caret past the twin
            context.setText("", 0);
            context.getComponent().getCaret().setDot(offset + 1);
            return;
        }
        if (PairLogic.shouldAutoClose(typed, next)) {
            context.setText(typed + String.valueOf(PairLogic.closerFor(typed)), 1);
        }
    }

    @Override
    public void afterInsert(Context context) {
    }

    @Override
    public void cancelled(Context context) {
    }

    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/javascript", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/typescript", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-java", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-c", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-cpp", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-python", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-ruby", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-rust", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-php5", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/sh", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-go", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-json", service = TypedTextInterceptor.Factory.class)
    })
    public static class Factory implements TypedTextInterceptor.Factory {
        @Override
        public TypedTextInterceptor createTypedTextInterceptor(
                org.netbeans.api.editor.mimelookup.MimePath mimePath) {
            return new JsTypedTextInterceptor();
        }
    }
}

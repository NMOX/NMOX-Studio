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
        @MimeRegistration(mimeType = "text/x-json", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-erlang", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-elixir", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-clojure", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-lisp", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-lua", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-swift", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-kotlin", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-csharp", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-fsharp", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-groovy", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-perl", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-r", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-julia", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-dart", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-scala", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-haskell", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-zig", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-gleam", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-nim", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-d", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-racket", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-elm", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-rescript", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-purescript", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-vlang", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-cairo", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-fortran", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-smalltalk", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-prolog", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-tcl", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-scheme", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-ada", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-pascal", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-odin", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-cobol", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-haxe", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-janet", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-ocaml", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-crystal", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/css", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-scss", service = TypedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-less", service = TypedTextInterceptor.Factory.class)
    })
    public static class Factory implements TypedTextInterceptor.Factory {
        @Override
        public TypedTextInterceptor createTypedTextInterceptor(
                org.netbeans.api.editor.mimelookup.MimePath mimePath) {
            return new JsTypedTextInterceptor();
        }
    }
}

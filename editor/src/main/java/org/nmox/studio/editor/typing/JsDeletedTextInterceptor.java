package org.nmox.studio.editor.typing;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.spi.editor.typinghooks.DeletedTextInterceptor;

/**
 * Pair-aware backspace: deleting the opener of an empty pair removes
 * its closer too, so "()" + backspace leaves nothing behind.
 */
public class JsDeletedTextInterceptor implements DeletedTextInterceptor {

    @Override
    public boolean beforeRemove(Context context) {
        return false;
    }

    @Override
    public void remove(Context context) throws BadLocationException {
        String removed = context.getText();
        if (removed.length() != 1 || !context.isBackwardDelete()) {
            return;
        }
        Document doc = context.getDocument();
        int offset = context.getOffset() - 1; // caret position after removal
        if (offset < 0 || offset >= doc.getLength()) {
            return;
        }
        char next = doc.getText(offset, 1).charAt(0);
        if (PairLogic.isEmptyPair(removed.charAt(0), next)) {
            doc.remove(offset, 1);
        }
    }

    @Override
    public void afterRemove(Context context) {
    }

    @Override
    public void cancelled(Context context) {
    }

    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/javascript", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/typescript", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-java", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-c", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-cpp", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-python", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-ruby", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-rust", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-php5", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/sh", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-go", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-json", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-erlang", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-elixir", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-clojure", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-lisp", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-lua", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-swift", service = DeletedTextInterceptor.Factory.class),
        @MimeRegistration(mimeType = "text/x-kotlin", service = DeletedTextInterceptor.Factory.class)
    })
    public static class Factory implements DeletedTextInterceptor.Factory {
        @Override
        public DeletedTextInterceptor createDeletedTextInterceptor(
                org.netbeans.api.editor.mimelookup.MimePath mimePath) {
            return new JsDeletedTextInterceptor();
        }
    }
}

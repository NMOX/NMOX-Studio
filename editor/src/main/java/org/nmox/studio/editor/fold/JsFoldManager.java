package org.nmox.studio.editor.fold;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.fold.Fold;
import org.netbeans.api.editor.fold.FoldType;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.api.lexer.Language;
import org.netbeans.spi.editor.fold.FoldHierarchyTransaction;
import org.netbeans.spi.editor.fold.FoldManager;
import org.netbeans.spi.editor.fold.FoldManagerFactory;
import org.netbeans.spi.editor.fold.FoldOperation;
import org.nmox.studio.editor.javascript.JavaScriptTokenId;
import org.nmox.studio.editor.typescript.TypeScriptLanguage;
import org.openide.util.RequestProcessor;

/**
 * Code folding for JS/TS: multi-line brace blocks fold to {...},
 * multi-line block comments to /*...*&#47;. Folds are recomputed from
 * the lexer on a short debounce after edits.
 */
public class JsFoldManager implements FoldManager {

    private static final RequestProcessor RP = new RequestProcessor("nmox-js-folds", 1);
    private static final int DEBOUNCE_MS = 400;

    private FoldOperation operation;
    private final List<Fold> managed = new ArrayList<>();
    private RequestProcessor.Task pending;

    @Override
    public void init(FoldOperation operation) {
        this.operation = operation;
    }

    @Override
    public void initFolds(FoldHierarchyTransaction transaction) {
        scheduleUpdate();
    }

    @Override
    public void insertUpdate(DocumentEvent evt, FoldHierarchyTransaction transaction) {
        scheduleUpdate();
    }

    @Override
    public void removeUpdate(DocumentEvent evt, FoldHierarchyTransaction transaction) {
        scheduleUpdate();
    }

    @Override
    public void changedUpdate(DocumentEvent evt, FoldHierarchyTransaction transaction) {
    }

    private void scheduleUpdate() {
        if (pending != null) {
            pending.cancel();
        }
        pending = RP.post(() -> javax.swing.SwingUtilities.invokeLater(this::rebuild), DEBOUNCE_MS);
    }

    private void rebuild() {
        if (operation == null) {
            return;
        }
        Document doc = operation.getHierarchy().getComponent().getDocument();
        String mime = (String) doc.getProperty("mimeType");
        Language<JavaScriptTokenId> language = "text/typescript".equals(mime)
                ? TypeScriptLanguage.language() : JavaScriptTokenId.language();

        final String[] text = new String[1];
        doc.render(() -> {
            try {
                text[0] = doc.getText(0, doc.getLength());
            } catch (BadLocationException ex) {
                text[0] = "";
            }
        });
        List<FoldScanner.Span> spans = FoldScanner.scan(text[0], language);

        operation.getHierarchy().lock();
        try {
            FoldHierarchyTransaction transaction = operation.openTransaction();
            try {
                for (Fold fold : managed) {
                    if (operation.owns(fold)) {
                        operation.removeFromHierarchy(fold, transaction);
                    }
                }
                managed.clear();
                int docLength = doc.getLength();
                for (FoldScanner.Span span : spans) {
                    if (span.end() <= docLength && span.end() - span.start() > 2) {
                        managed.add(operation.addToHierarchy(
                                span.comment() ? FoldType.COMMENT : FoldType.CODE_BLOCK,
                                span.comment() ? "/*...*/" : "{...}",
                                false,
                                span.start(), span.end(),
                                1, 1,
                                null, transaction));
                    }
                }
            } catch (BadLocationException ex) {
                // document changed under us; the next debounce gets it
            } finally {
                transaction.commit();
            }
        } finally {
            operation.getHierarchy().unlock();
        }
    }

    @Override
    public void removeEmptyNotify(Fold epmtyFold) {
    }

    @Override
    public void removeDamagedNotify(Fold damagedFold) {
    }

    @Override
    public void expandNotify(Fold expandedFold) {
    }

    @Override
    public void release() {
        if (pending != null) {
            pending.cancel();
        }
        managed.clear();
    }

    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/javascript", service = FoldManagerFactory.class),
        @MimeRegistration(mimeType = "text/typescript", service = FoldManagerFactory.class)
    })
    public static class Factory implements FoldManagerFactory {
        @Override
        public FoldManager createFoldManager() {
            return new JsFoldManager();
        }
    }
}

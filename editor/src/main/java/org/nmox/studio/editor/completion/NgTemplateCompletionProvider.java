package org.nmox.studio.editor.completion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

/**
 * Angular template completion (v1.217.0): {@code @} offers the Angular
 * 17+ control-flow blocks and {@code *} inside a tag offers the classic
 * structural directives — in Angular workspaces only. The rules live in
 * the pure {@link NgTemplateCompletion}; this class is the platform
 * adapter.
 *
 * <p>Registered on text/html — the mime {@code .component.html} keeps
 * (the grammars ride in as injections for the same reason, see
 * {@code NgTemplateGrammars}) — and gated per query on the file living
 * under an {@code angular.json}: a plain website's HTML never sees an
 * {@code @if} offer. It sits BESIDE {@code HtmlCompletionProvider}
 * (the platform collects every registered provider), adding Angular
 * rows to the same popup that already carries tags and attributes.
 */
@MimeRegistration(mimeType = "text/html", service = CompletionProvider.class)
public class NgTemplateCompletionProvider implements CompletionProvider {

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != CompletionProvider.COMPLETION_QUERY_TYPE) {
            return null;
        }
        return new AsyncCompletionTask(new Query(), component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        if (typedText.length() == 1
                && (typedText.charAt(0) == '@' || typedText.charAt(0) == '*')) {
            return CompletionProvider.COMPLETION_QUERY_TYPE;
        }
        return 0;
    }

    private static final class Query extends AsyncCompletionQuery {
        @Override
        protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
            try {
                File file = fileFor(doc);
                if (file == null || !NgTemplateCompletion.inAngularWorkspace(file)) {
                    return;
                }
                Element line = doc.getDefaultRootElement().getElement(
                        doc.getDefaultRootElement().getElementIndex(caretOffset));
                int lineStart = line.getStartOffset();
                String toCaret = doc.getText(lineStart, caretOffset - lineStart);
                List<NgTemplateCompletion.Item> items = NgTemplateCompletion.items(toCaret);
                if (!items.isEmpty()) {
                    int trigger = lineStart + NgTemplateCompletion.triggerOffset(toCaret);
                    for (NgTemplateCompletion.Item item : items) {
                        resultSet.addItem(new NgItem(item, trigger, caretOffset - trigger));
                    }
                }
            } catch (BadLocationException ex) {
                // caret moved under us; offer nothing rather than throw
            } finally {
                resultSet.finish();
            }
        }
    }

    private static File fileFor(Document doc) {
        Object sd = doc.getProperty(Document.StreamDescriptionProperty);
        if (sd instanceof DataObject dob) {
            FileObject fo = dob.getPrimaryFile();
            return fo == null ? null : FileUtil.toFile(fo);
        }
        if (sd instanceof FileObject fo) {
            return FileUtil.toFile(fo);
        }
        return null;
    }

    /** One Angular row: replaces from the trigger char with the construct. */
    private static final class NgItem implements CompletionItem {

        private final NgTemplateCompletion.Item item;
        private final int startOffset;
        private final int length;

        NgItem(NgTemplateCompletion.Item item, int startOffset, int length) {
            this.item = item;
            this.startOffset = startOffset;
            this.length = length;
        }

        @Override
        public void defaultAction(JTextComponent component) {
            if (CompletionEdits.replace(component.getDocument(),
                    startOffset, length, item.insert())) {
                component.setCaretPosition(startOffset + item.insert().length());
            }
            Completion.get().hideAll();
        }

        @Override
        public void processKeyEvent(KeyEvent evt) {
        }

        @Override
        public int getPreferredWidth(Graphics g, Font defaultFont) {
            return CompletionUtilities.getPreferredWidth(
                    item.insert() + " [" + item.label() + "]", null, g, defaultFont);
        }

        @Override
        public void render(Graphics g, Font defaultFont, Color defaultColor,
                Color backgroundColor, int width, int height, boolean selected) {
            CompletionUtilities.renderHtml(null, item.insert(),
                    "[" + item.label() + "]", g, defaultFont,
                    selected ? Color.WHITE : new Color(0xDD, 0x00, 0x31),
                    width, height, selected);
        }

        @Override
        public CompletionTask createDocumentationTask() {
            return null;
        }

        @Override
        public CompletionTask createToolTipTask() {
            return null;
        }

        @Override
        public boolean instantSubstitution(JTextComponent component) {
            return false;
        }

        @Override
        public int getSortPriority() {
            // above the generic HTML rows: at an @/* trigger the Angular
            // construct IS what the user is typing
            return 0;
        }

        @Override
        public CharSequence getSortText() {
            return item.insert();
        }

        @Override
        public CharSequence getInsertPrefix() {
            return item.insert();
        }
    }
}

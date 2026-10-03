package org.nmox.studio.editor.symbols.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.outline.OutlineKind;
import org.nmox.studio.editor.outline.OutlineModel.Item;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The provider's own work around the rule: reading the editor's text into
 * an outline, the row a symbol shows, and the jump.
 */
class FileSymbolSearchProviderTest {

    private static final String SOURCE = "class Cart {\n  addItem(item) {\n  }\n}\n\nfunction checkout() {\n}\n";

    private final List<String> said = new ArrayList<>();
    private Consumer<String> realStatus;

    @BeforeEach
    void captureStatus() {
        realStatus = FileSymbolSearchProvider.status;
        FileSymbolSearchProvider.status = said::add;
    }

    @AfterEach
    void restoreStatus() {
        FileSymbolSearchProvider.status = realStatus;
    }

    private static JTextArea editor(String mime, String text) {
        JTextArea area = new JTextArea(text);
        area.getDocument().putProperty("mimeType", mime);
        return area;
    }

    @Test
    @DisplayName("the outline is read from the document, as the language its kit gave it when it has no file")
    void outlineOfADocument() {
        JTextArea area = editor("text/javascript", SOURCE);
        List<Item> items = FileSymbolSearchProvider.outlineOf(area.getDocument());
        assertThat(items).extracting(Item::name).contains("Cart", "addItem", "checkout");
        assertThat(items).filteredOn(i -> i.name().equals("checkout")).extracting(Item::line).containsExactly(5);
        // a document with no mime at all is read as plain text, not refused
        assertThat(FileSymbolSearchProvider.mimeOf(new PlainDocument())).isEqualTo("text/plain");
    }

    @Test
    @DisplayName("a row names the symbol and its line, counted from one, and says what the outline adds")
    void rowLabels() {
        assertThat(FileSymbolSearchProvider.label(new Item(OutlineKind.FUNCTION, "checkout", null, 5, 0)))
                .isEqualTo("checkout — line 6");
        assertThat(FileSymbolSearchProvider.label(new Item(OutlineKind.TARGET, "GET /health", "healthCheck", 0, 0)))
                .isEqualTo("GET /health healthCheck — line 1");
        assertThat(FileSymbolSearchProvider.label(new Item(OutlineKind.FUNCTION, "f", "  ", 0, 0)))
                .isEqualTo("f — line 1");
    }

    @Test
    @DisplayName("Enter puts the caret at the start of the symbol's line")
    void jumpLandsOnTheLine() {
        JTextArea area = editor("text/javascript", SOURCE);
        Item checkout = new Item(OutlineKind.FUNCTION, "checkout", null, 5, 0);
        FileSymbolSearchProvider.jump(area, area.getDocument(), checkout);
        assertThat(area.getCaretPosition()).isEqualTo(SOURCE.indexOf("function checkout"));
        assertThat(said).isEmpty();
    }

    @Test
    @DisplayName("a line that is gone since the search is said, and the caret stays where it was")
    void aLineThatIsGone() {
        JTextArea area = editor("text/javascript", "a\nb");
        area.setCaretPosition(1);
        FileSymbolSearchProvider.jump(area, area.getDocument(), new Item(OutlineKind.FUNCTION, "gone", null, 40, 0));
        assertThat(area.getCaretPosition()).isEqualTo(1);
        assertThat(said).containsExactly("The file changed since the search: line 41 is no longer there");
    }

    @Test
    @DisplayName("an editor that has been given another document since the search is left alone")
    void anotherDocumentIsLeftAlone() {
        JTextArea area = editor("text/javascript", SOURCE);
        PlainDocument searched = new PlainDocument();
        area.setCaretPosition(3);
        FileSymbolSearchProvider.jump(area, searched, new Item(OutlineKind.FUNCTION, "checkout", null, 0, 0));
        assertThat(area.getCaretPosition()).isEqualTo(3);
        assertThat(said).isEmpty();
    }

    @Test
    @DisplayName("Go to Symbol finds Quick Search's field inside its toolbar presenter")
    void findsTheField() {
        JPanel presenter = new JPanel();
        JPanel inner = new JPanel();
        JTextField field = new JTextField();
        presenter.add(new JPanel());
        presenter.add(inner);
        inner.add(field);
        assertThat(GoToSymbolInFileAction.textFieldIn(presenter)).isSameAs(field);
        assertThat(GoToSymbolInFileAction.textFieldIn(field)).isSameAs(field);
        assertThat(GoToSymbolInFileAction.textFieldIn(new JPanel())).isNull();
        assertThat(GoToSymbolInFileAction.textFieldIn(null)).isNull();
    }

    @Test
    @DisplayName("the @ is typed when the focus lands, after the field's own focus handling, and only once")
    void typesOnceFocused() throws Exception {
        /** A field whose focus handling can be run without a focused window. */
        @SuppressWarnings("serial")
        final class Field extends JTextField {
            void gained() {
                processFocusEvent(new java.awt.event.FocusEvent(this, java.awt.event.FocusEvent.FOCUS_GAINED));
            }
        }
        Field field = new Field();
        field.setText("Search (Ctrl+I)"); // the hint the field shows while it waits
        // the field's own listener clears its hint at the focus, as Quick Search's does
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                field.setText("");
            }
        });
        SwingUtilities.invokeAndWait(() -> {
            GoToSymbolInFileAction.typeOnceFocused(field, "@");
            assertThat(field.getText()).as("nothing is typed before the focus arrives").isEqualTo("Search (Ctrl+I)");
            field.gained();
        });
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(field.getText()).isEqualTo("@");
        assertThat(field.getCaretPosition()).isEqualTo(1);
        // a later focus, after the user cleared the field, types nothing
        SwingUtilities.invokeAndWait(() -> {
            field.setText("");
            field.gained();
        });
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(field.getText()).isEmpty();
    }
}

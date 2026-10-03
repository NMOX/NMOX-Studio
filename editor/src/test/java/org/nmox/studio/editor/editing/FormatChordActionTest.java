package org.nmox.studio.editor.editing;

import java.awt.event.ActionEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JTextArea;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.EditorKit;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.editor.BaseKit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⇧⌥F on macOS: the kit's own format runs, and the Ï the chord also types
 * is swallowed (3.2.0 bound the chord straight to {@code format}, and the
 * character landed in the file).
 */
class FormatChordActionTest {

    private final List<String> ran = new ArrayList<>();

    /** A kit whose only interesting action is a recording "format". */
    @SuppressWarnings("serial")
    private final class KitWithFormat extends DefaultEditorKit {
        @Override
        public Action[] getActions() {
            return new Action[] {new AbstractAction(BaseKit.formatAction) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ran.add("format");
                }
            }};
        }
    }

    @SuppressWarnings("serial")
    private JTextArea editorWith(EditorKit kit) {
        JTextArea area = new JTextArea("let a;");
        area.setUI(new javax.swing.plaf.basic.BasicTextAreaUI() {
            @Override
            public EditorKit getEditorKit(JTextComponent tc) {
                return kit;
            }
        });
        return area;
    }

    private static ActionEvent press(JTextComponent on, int modifiers) {
        return new ActionEvent(on, ActionEvent.ACTION_PERFORMED, FormatChordAction.NAME, modifiers);
    }

    @Test
    @DisplayName("pressed as Shift+Opt+F it runs the kit's own format and arms the typed-echo guard, once")
    void theChordFormatsAndSwallowsItsCharacter() {
        JTextArea area = editorWith(new KitWithFormat());
        int before = area.getKeyListeners().length;
        TypedEchoTest.pressing(area,
                () -> new FormatChordAction().actionPerformed(press(area, ActionEvent.ALT_MASK | ActionEvent.SHIFT_MASK), area));
        assertThat(ran).containsExactly("format");
        assertThat(area.getKeyListeners()).as("the guard that eats the chord's own typed character")
                .hasSize(before + 1);
    }

    @Test
    @DisplayName("run without the Option chord (Quick Search, another binding) it formats and arms nothing")
    void withoutAltNothingIsSwallowed() {
        JTextArea area = editorWith(new KitWithFormat());
        int before = area.getKeyListeners().length;
        new FormatChordAction().actionPerformed(press(area, 0), area);
        new FormatChordAction().actionPerformed(press(area, ActionEvent.ALT_MASK | ActionEvent.META_MASK), area);
        assertThat(ran).containsExactly("format", "format");
        assertThat(area.getKeyListeners()).hasSize(before);
    }

    @Test
    @DisplayName("a kit with no format action, and no editor at all, do nothing and throw nothing")
    void nothingToFormat() {
        JTextArea plain = editorWith(new DefaultEditorKit());
        new FormatChordAction().actionPerformed(press(plain, ActionEvent.ALT_MASK | ActionEvent.SHIFT_MASK), plain);
        new FormatChordAction().actionPerformed(null, null);
        assertThat(ran).isEmpty();
        assertThat(FormatChordAction.formatOf(plain)).isNull();
    }

    @Test
    @DisplayName("the macOS file binds the chord to this action and never straight to format")
    void theChordIsBoundHere() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/org/nmox/studio/editor/vscode-format-keybindings-mac.xml"), StandardCharsets.UTF_8);
        String binds = xml.replaceAll("(?s)<!--.*?-->", "");
        assertThat(binds).contains("<bind actionName=\"" + FormatChordAction.NAME + "\" key=\"AS-F\"/>")
                .doesNotContain("actionName=\"format\"");
    }
}

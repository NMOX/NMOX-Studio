package org.nmox.studio.editor.present;

import java.awt.Component;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalFontTest {

    private static JLabel term(String family, int size) {
        JLabel l = new JLabel("term");
        l.setName("term");
        l.setFont(new Font(family, Font.PLAIN, size));
        return l;
    }

    @Test
    @DisplayName("every terminal under the open windows is bumped on entry and restored to ITS OWN font on leaving; other components untouched")
    void bumpsAndRestoresPerTerminal() {
        JPanel root = new JPanel();
        JPanel inner = new JPanel();
        JLabel a = term("Monospaced", 12);
        JLabel b = term("Monospaced", 14);
        JLabel other = new JLabel("not a terminal");
        other.setFont(new Font("Dialog", Font.PLAIN, 11));
        inner.add(b);
        root.add(a);
        root.add(inner);
        root.add(other);
        java.util.function.Predicate<Component> isTerm = c -> "term".equals(c.getName());
        assertThat(TerminalFont.follow(true, List.of(root), isTerm, 10)).isEqualTo(2);
        assertThat(a.getFont().getSize2D()).isEqualTo(22f);
        assertThat(b.getFont().getSize2D()).isEqualTo(24f);
        assertThat(other.getFont().getSize2D()).isEqualTo(11f);
        // a second entry is idempotent: the remembered font is the ORIGINAL, never the bumped one
        TerminalFont.follow(true, List.of(root), isTerm, 10);
        assertThat(a.getFont().getSize2D()).isEqualTo(22f);
        assertThat(TerminalFont.follow(false, List.of(root), isTerm, 10)).isEqualTo(2);
        assertThat(a.getFont().getSize2D()).isEqualTo(12f);
        assertThat(b.getFont().getSize2D()).isEqualTo(14f);
    }

    @Test
    @DisplayName("the walk stops at a terminal (its children are its own) and matches the platform Term by class name; the mode wires it after the Output follow")
    void wiring() throws Exception {
        JPanel root = new JPanel();
        JLabel t = term("Monospaced", 12);
        JLabel childOfTerm = term("Monospaced", 9); // a "terminal" inside a terminal: its own business, never listed
        JPanel fakeTerm = new JPanel();
        fakeTerm.setName("term");
        fakeTerm.add(childOfTerm);
        root.add(t);
        root.add(fakeTerm);
        assertThat(TerminalFont.find(root, c -> "term".equals(c.getName()))).containsExactly(t, fakeTerm);
        assertThat(TerminalFont.TERM_CLASS).isEqualTo("org.netbeans.lib.terminalemulator.Term");
        String mode = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/present/PresentationMode.java"));
        assertThat(mode.indexOf("TerminalFont.follow(enable)")).isGreaterThan(mode.indexOf("OutputFont.follow(enable)"));
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/present/TerminalFont.java"));
        assertThat(src.replaceAll("(?s)/\\*.*?\\*/", "")).doesNotContain("Preferences").doesNotContain("storeTo");
    }

    /** The terminal module's real shape: ActiveTerm extends StreamTerm extends Term. */
    static class StreamTermStub extends org.netbeans.lib.terminalemulator.Term {
    }

    static class ActiveTermStub extends StreamTermStub {
    }

    @Test
    @DisplayName("a SUBCLASS of the platform Term is a terminal — the exact-name match missed ActiveTerm in the live walk")
    void subclassesAreTerminals() {
        assertThat(TerminalFont.isTerm(ActiveTermStub.class)).isTrue();
        assertThat(TerminalFont.isTerm(org.netbeans.lib.terminalemulator.Term.class)).isTrue();
        assertThat(TerminalFont.isTerm(JPanel.class)).isFalse();
        assertThat(TerminalFont.IS_TERM.test(new ActiveTermStub())).isTrue();
    }
}

package org.nmox.studio.rack.projectstudio;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.accessibility.AccessibleText;
import javax.swing.JPanel;
import javax.swing.text.AttributeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.lib.terminalemulator.Term;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⌘-click in the Terminal (3.2): the gesture, the terminal recognised by
 * name, and the clicked link read through the screen's own
 * {@link AccessibleText} - modelled here the way the platform's
 * {@code Screen$AccessibleScreenText} answers (read from its bytecode):
 * each row's characters then one position for its line break, and the
 * SENTENCE at a position is that row's buffer.
 */
class TerminalLinkClicksTest {

    private static final int MENU = InputEvent.META_DOWN_MASK;

    @TempDir
    Path tmp;

    /** The terminal screen's accessible text, as its bytecode computes positions. */
    static class Screen implements AccessibleText {
        static final int CELL_W = 7;
        static final int CELL_H = 10;
        static final int COLS = 80;
        final List<String> rows = new ArrayList<>();
        /** Extra NUL cells the emulator's row buffer can carry past its text. */
        int nulPadding;
        boolean assertsOnSentence;

        Screen(String... rows) {
            this.rows.addAll(List.of(rows));
        }

        int start(int row) {
            int p = 0;
            for (int r = 0; r < row; r++) {
                p += rows.get(r).length() + 1;
            }
            return p;
        }

        int rowOf(int index) {
            int p = 0;
            for (int r = 0; r < rows.size(); r++) {
                p += rows.get(r).length() + 1;
                if (p > index) {
                    return r;
                }
            }
            return -1;
        }

        @Override
        public int getIndexAtPoint(Point p) {
            int row = Math.min(rows.size() - 1, Math.max(0, p.y / CELL_H));
            int col = Math.min(COLS, Math.max(0, (p.x - 3) / CELL_W)); // a small glyph gutter, clipped like BCoord.clip
            return start(row) + col;
        }

        @Override
        public String getAtIndex(int part, int index) {
            int r = rowOf(index);
            if (r < 0) {
                return null;
            }
            String row = rows.get(r);
            if (part == AccessibleText.SENTENCE) {
                if (assertsOnSentence) {
                    throw new AssertionError(); // Line.toString() carries an assert false
                }
                return row + "\0".repeat(nulPadding);
            }
            if (part == AccessibleText.WORD) {
                // the emulator's default delimiters include the space, ( ) and quotes
                int c = index - start(r);
                String delims = " ()\"'";
                if (c >= row.length() || delims.indexOf(row.charAt(c)) >= 0) {
                    return null;
                }
                int b = c;
                while (b > 0 && delims.indexOf(row.charAt(b - 1)) < 0) {
                    b--;
                }
                int e = c;
                while (e < row.length() && delims.indexOf(row.charAt(e)) < 0) {
                    e++;
                }
                return row.substring(b, e);
            }
            if (part == AccessibleText.CHARACTER) {
                int c = index - start(r);
                return c < row.length() ? String.valueOf(row.charAt(c)) : "\0";
            }
            return null;
        }

        @Override
        public String getAfterIndex(int part, int index) {
            return null;
        }

        @Override
        public String getBeforeIndex(int part, int index) {
            return null;
        }

        @Override
        public Rectangle getCharacterBounds(int i) {
            return null;
        }

        @Override
        public int getCharCount() {
            return start(rows.size());
        }

        @Override
        public int getCaretPosition() {
            return 0;
        }

        @Override
        public AttributeSet getCharacterAttribute(int i) {
            return null;
        }

        @Override
        public int getSelectionStart() {
            return 0;
        }

        @Override
        public int getSelectionEnd() {
            return 0;
        }

        @Override
        public String getSelectedText() {
            return null;
        }
    }

    private static Point at(int row, int col) {
        return new Point(3 + col * Screen.CELL_W + 2, row * Screen.CELL_H + 4);
    }

    @Test
    @DisplayName("the gesture: one menu-modifier click of the first button, nothing else")
    void gesture() {
        assertThat(TerminalLinkClicks.isLinkGesture(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, 1, MENU, MENU)).isTrue();
        assertThat(TerminalLinkClicks.isLinkGesture(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, 1, 0, MENU))
                .as("a plain click selects, as it always has").isFalse();
        assertThat(TerminalLinkClicks.isLinkGesture(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, 2, MENU, MENU))
                .as("a double click selects a word").isFalse();
        assertThat(TerminalLinkClicks.isLinkGesture(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON3, 1, MENU, MENU)).isFalse();
        assertThat(TerminalLinkClicks.isLinkGesture(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 1, MENU, MENU)).isFalse();
        assertThat(TerminalLinkClicks.isLinkGesture(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, 1,
                MENU | InputEvent.ALT_DOWN_MASK, MENU)).isFalse();
        assertThat(TerminalLinkClicks.isLinkGesture(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, 1,
                InputEvent.CTRL_DOWN_MASK, InputEvent.CTRL_DOWN_MASK)).as("Ctrl off the Mac").isTrue();
    }

    @Test
    @DisplayName("the terminal is recognised by its class name, through a subclass and from a child")
    void insideTerm() {
        class ActiveTermLike extends Term {
        }
        Term term = new ActiveTermLike();
        JPanel screen = new JPanel();
        term.add(screen);
        assertThat(TerminalLinkClicks.insideTerm(screen)).isTrue();
        assertThat(TerminalLinkClicks.insideTerm(term)).isTrue();
        assertThat(TerminalLinkClicks.insideTerm(new JPanel())).isFalse();
    }

    @Test
    @DisplayName("the clicked row and column pick the link under the pointer")
    void clickedLink() {
        Screen s = new Screen("$ npx tsc", "a.ts:1:2 imported from b.ts:3:4", "$ ");
        assertThat(TerminalLinkClicks.linkAt(s, at(1, 1)).path()).isEqualTo("a.ts");
        assertThat(TerminalLinkClicks.linkAt(s, at(1, 25)).path()).isEqualTo("b.ts");
        assertThat(TerminalLinkClicks.linkAt(s, at(1, 10))).as("between the links").isNull();
        assertThat(TerminalLinkClicks.linkAt(s, at(0, 3))).as("a row with no link").isNull();
    }

    @Test
    @DisplayName("a click past the end of a row's text follows nothing, even when the next row has a link")
    void pastTheEnd() {
        Screen s = new Screen("ok", "src/app.ts:4:2 - error");
        assertThat(TerminalLinkClicks.linkAt(s, at(0, 5))).isNull();
    }

    @Test
    @DisplayName("NUL cells past the row's text are not part of it")
    void nulPadding() {
        Screen s = new Screen("  --> src/main.rs:3:5");
        s.nulPadding = 40;
        assertThat(TerminalLinkClicks.linkAt(s, at(0, 10)).path()).isEqualTo("src/main.rs");
        assertThat(TerminalLinkClicks.linkAt(s, at(0, 30))).isNull();
    }

    @Test
    @DisplayName("a screen whose row reader fails (a JVM run with -ea) falls back to the word under the pointer")
    void unreadableRowFallsBackToTheWord() {
        Screen s = new Screen("    at run (/srv/app/index.js:7:13) and then");
        s.assertsOnSentence = true;
        assertThat(TerminalLinkClicks.linkAt(s, at(0, 20)).path()).isEqualTo("/srv/app/index.js");
        assertThat(TerminalLinkClicks.linkAt(s, at(0, 5)))
                .as("a word that names no place is no link, and nothing throws").isNull();
    }

    @Test
    @DisplayName("when the column cannot be trusted, the row's only link and never a guess between two")
    void untrustedColumn() {
        Screen one = new Screen("  --> src/main.rs:3:5") {
            @Override
            public String getAtIndex(int part, int index) {
                return part == AccessibleText.CHARACTER ? "?" : super.getAtIndex(part, index);
            }
        };
        assertThat(TerminalLinkClicks.linkAt(one, at(0, 1)).path()).isEqualTo("src/main.rs");
        Screen two = new Screen("a.ts:1:2 imported from b.ts:3:4") {
            @Override
            public String getAtIndex(int part, int index) {
                return part == AccessibleText.CHARACTER ? "?" : super.getAtIndex(part, index);
            }
        };
        assertThat(TerminalLinkClicks.linkAt(two, at(0, 1))).isNull();
    }

    @Test
    @DisplayName("a relative link with no project says so; with one it names the file under it")
    void targets() {
        TerminalLinks.Link rel = TerminalLinks.find("src/app.ts:4:2").get(0);
        TerminalLinkClicks.Target none = TerminalLinkClicks.target(rel, null, tmp.toFile());
        assertThat(none.file()).isNull();
        assertThat(none.refusal()).contains("src/app.ts");
        File project = tmp.resolve("proj").toFile();
        TerminalLinkClicks.Target some = TerminalLinkClicks.target(rel, project, tmp.toFile());
        assertThat(some.file()).isEqualTo(new File(project, "src/app.ts"));
        assertThat(some.refusal()).isNull();
    }
}

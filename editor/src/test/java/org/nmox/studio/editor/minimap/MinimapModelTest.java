package org.nmox.studio.editor.minimap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinimapModelTest {

    @Test
    @DisplayName("Shapes record indent and length per line; the last line needs no newline")
    void shapesPerLine() {
        MinimapModel.Shapes s = MinimapModel.shape("abc\n  def\n\n    x");
        assertThat(s.lines()).isEqualTo(4);
        assertThat(s.indents()).containsExactly(0, 2, 0, 4);
        assertThat(s.lengths()).containsExactly(3, 5, 0, 5);
        assertThat(s.truncated()).isFalse();
        assertThat(MinimapModel.shape("").lines()).isEqualTo(1);
        assertThat(MinimapModel.shape("a\n").lines()).isEqualTo(1);
    }

    @Test
    @DisplayName("A document past MAX_LINES is truncated at the cap and says so")
    void truncatedAtCap() {
        String text = "x\n".repeat(MinimapModel.MAX_LINES + 5);
        MinimapModel.Shapes s = MinimapModel.shape(text);
        assertThat(s.lines()).isEqualTo(MinimapModel.MAX_LINES);
        assertThat(s.truncated()).isTrue();
        String exact = "x\n".repeat(MinimapModel.MAX_LINES);
        assertThat(MinimapModel.shape(exact).truncated()).isFalse();
    }

    @Test
    @DisplayName("Rows keep the preferred height until the document is taller than the strip, then scale to fit")
    void rowHeightScales() {
        assertThat(MinimapModel.rowHeight(100, 800)).isEqualTo(MinimapModel.PREFERRED_ROW);
        assertThat(MinimapModel.rowHeight(1600, 800)).isEqualTo(0.5);
        assertThat(MinimapModel.rowHeight(0, 800)).isEqualTo(MinimapModel.PREFERRED_ROW);
        // the whole document always fits: the last line's top is inside the strip
        int lines = 5000;
        double row = MinimapModel.rowHeight(lines, 800);
        assertThat(MinimapModel.yOf(lines - 1, row)).isLessThan(800);
    }

    @Test
    @DisplayName("y↔line round-trips and clamps into the document")
    void yToLineAndBack() {
        double row = MinimapModel.rowHeight(300, 800); // 2 px rows
        assertThat(MinimapModel.yOf(10, row)).isEqualTo(20);
        assertThat(MinimapModel.lineAt(20, row, 300)).isEqualTo(10);
        assertThat(MinimapModel.lineAt(21, row, 300)).isEqualTo(10);
        assertThat(MinimapModel.lineAt(-5, row, 300)).isEqualTo(0);
        assertThat(MinimapModel.lineAt(5000, row, 300)).isEqualTo(299);
        assertThat(MinimapModel.lineAt(40, row, 0)).isEqualTo(0);
    }

    @Test
    @DisplayName("Bars run from indent to length, capped at MAX_COLS, empty for blank lines")
    void bars() {
        assertThat(MinimapModel.bar(0, 120, 120)).containsExactly(0, 120);
        assertThat(MinimapModel.bar(4, 60, 120)).containsExactly(4, 56);
        assertThat(MinimapModel.bar(0, 100_000, 120)).containsExactly(0, 120);
        assertThat(MinimapModel.bar(0, 0, 120)).containsExactly(0, 0);
        assertThat(MinimapModel.bar(3, 3, 120)).containsExactly(0, 0);
        // a one-column bar still paints one pixel in a narrow strip
        assertThat(MinimapModel.bar(0, 1, 40)[1]).isEqualTo(1);
    }
}

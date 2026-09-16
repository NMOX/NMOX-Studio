package org.nmox.studio.core.util;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TableColumnsTest {

    private static int rendered(JTable t, int row, int col) {
        return t.prepareRenderer(t.getCellRenderer(row, col), row, col).getPreferredSize().width;
    }

    @Test
    @DisplayName("a column is at least as wide as its widest value — no 'Noor H...' beside empty space")
    void columnFitsItsWidestValue() {
        JTable t = new JTable(new DefaultTableModel(
                new Object[][] {{"Ada"}, {"Noor Haddad-Whitfield"}}, new Object[] {"name"}));
        TableColumns.fitToContent(t);
        assertThat(t.getColumnModel().getColumn(0).getPreferredWidth())
                .isGreaterThanOrEqualTo(rendered(t, 1, 0));
    }

    @Test
    @DisplayName("one enormous value is capped, so it cannot push every other column off screen")
    void enormousValueIsCapped() {
        JTable t = new JTable(new DefaultTableModel(
                new Object[][] {{"x".repeat(2_000)}}, new Object[] {"blob"}));
        TableColumns.fitToContent(t);
        assertThat(t.getColumnModel().getColumn(0).getPreferredWidth()).isEqualTo(TableColumns.MAX_WIDTH);
    }

    @Test
    @DisplayName("a long header with short values still shows its header")
    void headerIsNeverCut() {
        JTable t = new JTable(new DefaultTableModel(
                new Object[][] {{"1"}}, new Object[] {"orders_placed_this_quarter"}));
        TableColumns.fitToContent(t);
        int header = t.getTableHeader().getDefaultRenderer()
                .getTableCellRendererComponent(t, "orders_placed_this_quarter", false, false, -1, 0)
                .getPreferredSize().width;
        assertThat(t.getColumnModel().getColumn(0).getPreferredWidth()).isGreaterThanOrEqualTo(header);
    }
}

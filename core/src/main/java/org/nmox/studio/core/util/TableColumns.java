package org.nmox.studio.core.util;

import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * Sizes a result grid's columns to what they hold (v2.163.0).
 *
 * <p>A JTable left to itself gives every column the same 75px, and with
 * {@code AUTO_RESIZE_OFF} — right for results, which should scroll rather
 * than squash — that is what the reader gets: DB Studio showed a customer
 * table reading {@code Noor H...}, {@code Tom Ri...}, {@code Ines D...} beside
 * hundreds of empty pixels. A person scans a grid by its values, so a column
 * is as wide as its widest sampled value or its header, whichever is wider.
 *
 * <p>Two bounds keep it honest. Only the first {@link #SAMPLE_ROWS} rows are
 * measured, so a large result costs a fixed amount on the paint thread; and
 * no column grows past {@link #MAX_WIDTH}, so one long value (a JSON blob, a
 * truncated LOB) cannot push every other column off screen — it elides, and
 * the whole value is a click away as it was before.
 */
public final class TableColumns {

    private TableColumns() {
    }

    /** Rows measured per column; enough to see a result's shape, bounded in cost. */
    public static final int SAMPLE_ROWS = 200;

    /** No column is wider than this; a longer value elides as before. */
    public static final int MAX_WIDTH = 360;

    /** Breathing room between a value and the next column's edge. */
    static final int PADDING = 14;

    public static void fitToContent(JTable table) {
        fitToContent(table, SAMPLE_ROWS, MAX_WIDTH);
    }

    public static void fitToContent(JTable table, int sampleRows, int maxWidth) {
        TableColumnModel columns = table.getColumnModel();
        int rows = Math.min(table.getRowCount(), Math.max(0, sampleRows));
        for (int c = 0; c < columns.getColumnCount(); c++) {
            TableColumn column = columns.getColumn(c);
            int widest = headerWidth(table, column, c);
            for (int r = 0; r < rows && widest < maxWidth; r++) {
                Component cell = table.prepareRenderer(table.getCellRenderer(r, c), r, c);
                widest = Math.max(widest, cell.getPreferredSize().width);
            }
            column.setPreferredWidth(Math.min(maxWidth, widest + PADDING));
        }
    }

    private static int headerWidth(JTable table, TableColumn column, int index) {
        TableCellRenderer renderer = column.getHeaderRenderer();
        JTableHeader header = table.getTableHeader();
        if (renderer == null && header != null) {
            renderer = header.getDefaultRenderer();
        }
        if (renderer == null) {
            return 0;
        }
        return renderer.getTableCellRendererComponent(table, column.getHeaderValue(),
                false, false, -1, index).getPreferredSize().width;
    }
}

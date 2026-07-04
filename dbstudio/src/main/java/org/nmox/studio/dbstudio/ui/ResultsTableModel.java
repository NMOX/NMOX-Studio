package org.nmox.studio.dbstudio.ui;

import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;
import org.nmox.studio.dbstudio.engine.QueryResult;

/**
 * One {@link QueryResult} as a read-only Swing {@code TableModel}. The
 * engine already stringifies every cell (SQL {@code NULL} arrives as the
 * string {@code "NULL"}); this model adds the defensive last mile — a
 * genuinely absent cell (null, or a ragged row shorter than the header)
 * also renders as {@code "NULL"} rather than blowing up the grid.
 *
 * <p>Pure model logic, headless-testable; the JTable that displays it
 * lives in the (coverage-excluded) TopComponent.
 */
final class ResultsTableModel extends AbstractTableModel {

    /** What an absent cell renders as, matching the engine's convention. */
    static final String NULL_TEXT = "NULL";

    private final QueryResult result;

    ResultsTableModel(QueryResult result) {
        this.result = Objects.requireNonNull(result, "result");
    }

    @Override
    public int getRowCount() {
        return result.rows().size();
    }

    @Override
    public int getColumnCount() {
        return result.columnNames().size();
    }

    @Override
    public String getColumnName(int column) {
        return result.columnNames().get(column);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false; // the grid is a viewer, never an editor
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        List<String> row = result.rows().get(rowIndex);
        if (columnIndex >= row.size()) {
            return NULL_TEXT; // ragged row: fewer cells than headers
        }
        String value = row.get(columnIndex);
        return value == null ? NULL_TEXT : value;
    }
}

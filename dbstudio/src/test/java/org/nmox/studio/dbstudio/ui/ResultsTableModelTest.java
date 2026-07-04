package org.nmox.studio.dbstudio.ui;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.engine.QueryResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryResult poured into a Swing TableModel: headers from the result's
 * column names, read-only cells, and the defensive NULL last mile for
 * absent values and ragged rows.
 */
class ResultsTableModelTest {

    private static QueryResult grid() {
        return new QueryResult(
                List.of("id", "name"),
                List.of(List.of("1", "ada"), List.of("2", "grace")),
                2, -1, false, 5, null, "SELECT id, name FROM users");
    }

    @Test
    @DisplayName("column names and counts mirror the result")
    void columnsAndCounts() {
        ResultsTableModel model = new ResultsTableModel(grid());

        assertThat(model.getColumnCount()).isEqualTo(2);
        assertThat(model.getRowCount()).isEqualTo(2);
        assertThat(model.getColumnName(0)).isEqualTo("id");
        assertThat(model.getColumnName(1)).isEqualTo("name");
    }

    @Test
    @DisplayName("cell values come through stringified")
    void values() {
        ResultsTableModel model = new ResultsTableModel(grid());

        assertThat(model.getValueAt(0, 0)).isEqualTo("1");
        assertThat(model.getValueAt(1, 1)).isEqualTo("grace");
    }

    @Test
    @DisplayName("a genuinely null cell renders as NULL")
    void nullCell() {
        QueryResult result = new QueryResult(
                List.of("id", "nick"),
                List.of(Arrays.asList("1", null)),
                1, -1, false, 1, null, "SELECT id, nick FROM users");
        ResultsTableModel model = new ResultsTableModel(result);

        assertThat(model.getValueAt(0, 1)).isEqualTo("NULL");
    }

    @Test
    @DisplayName("a ragged row shorter than the header renders NULL, not an exception")
    void raggedRow() {
        QueryResult result = new QueryResult(
                List.of("a", "b", "c"),
                List.of(List.of("only-a")),
                1, -1, false, 1, null, "SELECT a, b, c FROM t");
        ResultsTableModel model = new ResultsTableModel(result);

        assertThat(model.getValueAt(0, 0)).isEqualTo("only-a");
        assertThat(model.getValueAt(0, 1)).isEqualTo("NULL");
        assertThat(model.getValueAt(0, 2)).isEqualTo("NULL");
    }

    @Test
    @DisplayName("the grid is a viewer: no cell is editable, cells are strings")
    void readOnlyStrings() {
        ResultsTableModel model = new ResultsTableModel(grid());

        assertThat(model.isCellEditable(0, 0)).isFalse();
        assertThat(model.isCellEditable(1, 1)).isFalse();
        assertThat(model.getColumnClass(0)).isEqualTo(String.class);
    }

    @Test
    @DisplayName("an update result is an empty grid")
    void updateResult() {
        QueryResult update = new QueryResult(
                List.of(), List.of(), 0, 3, false, 2, null, "DELETE FROM t");
        ResultsTableModel model = new ResultsTableModel(update);

        assertThat(model.getColumnCount()).isZero();
        assertThat(model.getRowCount()).isZero();
    }
}

package org.nmox.studio.dbstudio.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

/**
 * The dirty state of one editable result grid: which cells the user
 * changed and what {@code UPDATE} statements those changes translate
 * to. Pure model — no Swing, fully unit-testable; the grid's table
 * model delegates here and the (coverage-excluded) TopComponent only
 * wires buttons to {@link #statements}, {@link #revert} and
 * {@link #dirtyCount}.
 *
 * <p><b>The NULL convention</b>, inherited from the grid itself: the
 * engine stringifies SQL {@code NULL} as the text {@code "NULL"}
 * ({@link QueryResult#rows}), so this session speaks the same dialect
 * in both directions — {@link #valueAt} renders an absent cell as
 * {@code "NULL"}, and a user who types {@code NULL} into a cell means
 * SQL {@code NULL} ({@link #statements} passes it to
 * {@link UpdateBuilder} as a Java {@code null}, which renders the
 * {@code NULL} keyword). The four-letter string {@code 'NULL'} is
 * therefore not enterable through the grid — the same deliberate
 * ambiguity-refusal as UpdateBuilder's primary-key rule.
 *
 * <p>Editing a cell back to its original text un-dirties it — the
 * session tracks differences, not gestures. {@link #statements} emits
 * one primary-key-scoped UPDATE per dirty row, rows in ascending grid
 * order (deterministic), and propagates {@link UpdateBuilder}'s
 * {@link IllegalArgumentException} refusals verbatim — those messages
 * were written for the status bar.
 */
public final class EditSession {

    /** One grid cell, identified by model row and column. */
    private record Cell(int row, int column) {
    }

    private final TableInfo table;
    private final List<ColumnInfo> columns;
    private final List<String> columnNames;
    private final List<List<String>> originalRows;
    private final Map<Cell, String> edits = new HashMap<>();

    /**
     * @param result  the grid being edited; its rows are the "original"
     *                values every edit is compared against
     * @param table   the one table the query selected from
     * @param columns the table's column metadata (types + PK flags)
     */
    public EditSession(QueryResult result, TableInfo table, List<ColumnInfo> columns) {
        Objects.requireNonNull(result, "result");
        this.table = Objects.requireNonNull(table, "table");
        this.columns = List.copyOf(columns);
        this.columnNames = result.columnNames(); // already immutable
        this.originalRows = result.rows();
    }

    /** The table the UPDATEs will target. */
    public TableInfo table() {
        return table;
    }

    /** The grid's column labels, in grid order. */
    public List<String> columnNames() {
        return columnNames;
    }

    /** How many rows the grid holds. */
    public int rowCount() {
        return originalRows.size();
    }

    /**
     * Whether the given grid column accepts edits at all: it must be a
     * real column of the table (not an alias or expression) and not
     * part of the primary key (the key addresses the row being
     * updated). The table model uses this for
     * {@code isCellEditable}, so UpdateBuilder's corresponding
     * refusals are unreachable from the UI — but still enforced.
     */
    public boolean editableColumn(int column) {
        if (column < 0 || column >= columnNames.size()) {
            return false;
        }
        ColumnInfo info = columnNamed(columnNames.get(column));
        return info != null && !info.primaryKey();
    }

    /**
     * Records that the user set the cell to {@code newValue}
     * ({@code null} is normalized to the grid's {@code "NULL"} text).
     * Setting a cell back to its original text clears its dirty state.
     */
    public void edit(int row, int column, String newValue) {
        Objects.checkIndex(row, originalRows.size());
        Objects.checkIndex(column, columnNames.size());
        String normalized = newValue == null ? "NULL" : newValue;
        Cell cell = new Cell(row, column);
        if (normalized.equals(originalText(row, column))) {
            edits.remove(cell);
        } else {
            edits.put(cell, normalized);
        }
    }

    /** The text the grid should show for a cell: the edit if dirty, else the original. */
    public String valueAt(int row, int column) {
        String edited = edits.get(new Cell(row, column));
        return edited != null ? edited : originalText(row, column);
    }

    /** True when the cell currently differs from its original value. */
    public boolean isDirty(int row, int column) {
        return edits.containsKey(new Cell(row, column));
    }

    /** How many cells currently differ from their original values. */
    public int dirtyCount() {
        return edits.size();
    }

    /** How many distinct rows have at least one dirty cell. */
    public int dirtyRowCount() {
        return (int) edits.keySet().stream().mapToInt(Cell::row).distinct().count();
    }

    /** Forgets every edit; the grid shows original values again. */
    public void revert() {
        edits.clear();
    }

    /**
     * One primary-key-scoped {@code UPDATE} per dirty row, rows in
     * ascending grid order — same edits, same statements, always.
     * A cell edited to the text {@code NULL} becomes SQL {@code NULL}
     * (see the class javadoc's NULL convention).
     *
     * @throws IllegalArgumentException whenever {@link UpdateBuilder}
     *         refuses — the message is status-bar-ready; show it
     *         verbatim
     */
    public List<String> statements(DbEngine engine) {
        SortedMap<Integer, SortedMap<Integer, String>> byRow = new TreeMap<>();
        for (Map.Entry<Cell, String> edit : edits.entrySet()) {
            byRow.computeIfAbsent(edit.getKey().row(), r -> new TreeMap<>())
                    .put(edit.getKey().column(),
                            "NULL".equals(edit.getValue()) ? null : edit.getValue());
        }
        return byRow.entrySet().stream()
                .map(rowEdits -> UpdateBuilder.update(engine, table, columns, columnNames,
                        originalRows.get(rowEdits.getKey()), rowEdits.getValue()))
                .toList();
    }

    private String originalText(int row, int column) {
        List<String> cells = originalRows.get(row);
        if (column >= cells.size()) {
            return "NULL"; // ragged row, same convention as the read-only grid
        }
        String value = cells.get(column);
        return value == null ? "NULL" : value;
    }

    private ColumnInfo columnNamed(String name) {
        for (ColumnInfo column : columns) {
            if (column.name().equalsIgnoreCase(name)) {
                return column;
            }
        }
        return null;
    }
}

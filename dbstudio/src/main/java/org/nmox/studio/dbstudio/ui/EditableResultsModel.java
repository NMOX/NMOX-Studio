package org.nmox.studio.dbstudio.ui;

import java.util.Objects;
import javax.swing.table.AbstractTableModel;
import org.nmox.studio.dbstudio.engine.EditSession;

/**
 * The editable sibling of {@link ResultsTableModel}: one gated result
 * grid backed by an {@link EditSession}. Cells are editable exactly
 * when the session says the column is a real, non-primary-key table
 * column; every committed edit lands in the session (which un-dirties
 * a cell edited back to its original text) and pings
 * {@code dirtyChanged} so the toolbar chip and Apply/Revert buttons
 * can follow.
 *
 * <p>Pure model logic, headless-testable; the JTable, tint renderer
 * and buttons live in the (coverage-excluded) TopComponent.
 */
final class EditableResultsModel extends AbstractTableModel {

    private final transient EditSession session;
    private final transient Runnable dirtyChanged;

    EditableResultsModel(EditSession session, Runnable dirtyChanged) {
        this.session = Objects.requireNonNull(session, "session");
        this.dirtyChanged = Objects.requireNonNull(dirtyChanged, "dirtyChanged");
    }

    /** The session tracking this grid's edits. */
    EditSession session() {
        return session;
    }

    @Override
    public int getRowCount() {
        return session.rowCount();
    }

    @Override
    public int getColumnCount() {
        return session.columnNames().size();
    }

    @Override
    public String getColumnName(int column) {
        return session.columnNames().get(column);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    /** Real, non-primary-key table columns only — the session decides. */
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return session.editableColumn(columnIndex);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return session.valueAt(rowIndex, columnIndex);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        session.edit(rowIndex, columnIndex, aValue == null ? null : aValue.toString());
        fireTableCellUpdated(rowIndex, columnIndex);
        dirtyChanged.run();
    }

    /** True when the cell holds an uncommitted edit — feeds the tint renderer. */
    boolean isDirty(int rowIndex, int columnIndex) {
        return session.isDirty(rowIndex, columnIndex);
    }

    /** Forgets every pending edit and repaints the grid. */
    void revertAll() {
        session.revert();
        fireTableDataChanged();
        dirtyChanged.run();
    }
}

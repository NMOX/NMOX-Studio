package org.nmox.studio.apiclient.ui;

import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.nmox.studio.apiclient.model.ApiModel.Assertion;

/**
 * The editable grid behind the Tests tab: an assertion kind and its
 * target value per row, with a blank trailing row that adds a new
 * assertion when filled.
 */
final class TestsTableModel extends AbstractTableModel {

    private final List<Assertion> tests;
    private final Runnable onChange;

    TestsTableModel(List<Assertion> tests, Runnable onChange) {
        this.tests = tests;
        this.onChange = onChange;
    }

    @Override
    public int getRowCount() {
        return tests.size() + 1;
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int col) {
        return col == 0 ? "Assertion" : "Target";
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return true;
    }

    @Override
    public Object getValueAt(int row, int col) {
        if (row >= tests.size()) {
            return col == 0 ? Assertion.Kind.STATUS_IS : "";
        }
        Assertion a = tests.get(row);
        return col == 0 ? a.kind : a.target;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        Assertion a;
        if (row >= tests.size()) {
            if (value == null || value.toString().isEmpty()) {
                return;
            }
            a = new Assertion();
            tests.add(a);
        } else {
            a = tests.get(row);
        }
        if (col == 0) {
            a.kind = value instanceof Assertion.Kind k ? k : Assertion.Kind.valueOf(value.toString());
        } else {
            a.target = value.toString();
        }
        fireTableDataChanged();
        onChange.run();
    }

    /** The combo editor for the Assertion column. */
    static javax.swing.table.TableColumn install(javax.swing.JTable table) {
        javax.swing.JComboBox<Assertion.Kind> combo =
                new javax.swing.JComboBox<>(Assertion.Kind.values());
        javax.swing.table.TableColumn col = table.getColumnModel().getColumn(0);
        col.setCellEditor(new javax.swing.DefaultCellEditor(combo));
        return col;
    }
}

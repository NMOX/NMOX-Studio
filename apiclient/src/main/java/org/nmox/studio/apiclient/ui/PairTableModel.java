package org.nmox.studio.apiclient.ui;

import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.nmox.studio.apiclient.model.ApiModel.Pair;

/**
 * The editable grid behind the Params and Headers tabs: an on/off
 * checkbox, a name, and a value, with a blank trailing row that
 * materializes a new Pair the moment you type in it.
 */
final class PairTableModel extends AbstractTableModel {

    private final List<Pair> pairs;
    private final Runnable onChange;

    PairTableModel(List<Pair> pairs, Runnable onChange) {
        this.pairs = pairs;
        this.onChange = onChange;
    }

    @Override
    public int getRowCount() {
        return pairs.size() + 1; // trailing blank row for adding
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public String getColumnName(int col) {
        return switch (col) {
            case 0 -> "On";
            case 1 -> "Name";
            default -> "Value";
        };
    }

    @Override
    public Class<?> getColumnClass(int col) {
        return col == 0 ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return true;
    }

    @Override
    public Object getValueAt(int row, int col) {
        if (row >= pairs.size()) {
            return col == 0 ? Boolean.TRUE : "";
        }
        Pair p = pairs.get(row);
        return switch (col) {
            case 0 -> p.enabled;
            case 1 -> p.name == null ? "" : p.name;
            default -> p.value == null ? "" : p.value;
        };
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        Pair p;
        if (row >= pairs.size()) {
            if (col == 0 || value == null || value.toString().isEmpty()) {
                return; // don't create a row from an empty edit
            }
            p = new Pair("", "");
            pairs.add(p);
        } else {
            p = pairs.get(row);
        }
        switch (col) {
            case 0 -> p.enabled = Boolean.TRUE.equals(value);
            case 1 -> p.name = value.toString();
            default -> p.value = value.toString();
        }
        // drop a row emptied of both name and value
        if ((p.name == null || p.name.isEmpty()) && (p.value == null || p.value.isEmpty())
                && row < pairs.size()) {
            pairs.remove(row);
        }
        fireTableDataChanged();
        onChange.run();
    }
}

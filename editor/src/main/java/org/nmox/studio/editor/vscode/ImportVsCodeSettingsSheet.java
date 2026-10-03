package org.nmox.studio.editor.vscode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.nmox.studio.core.util.PlainDialogs;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.core.util.PlainTables;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.core.util.TextDirection;
import org.nmox.studio.editor.vscode.ImportVsCodeSettingsAction.Prepared;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Fit;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Location;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Outcome;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Row;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;

/**
 * The import sheet: one row per setting the file holds that this product
 * recognises, each with a checkbox, and a footer counting the rest
 * without naming them. Thin on purpose: every word in a row was decided
 * by {@link VsCodeUserSettings} before it got here.
 *
 * <p>The values are a file's text, so every cell paints through
 * {@link PlainTables} and the key and value columns keep left-to-right
 * order in a mirrored window (they are machine text). Only rows Apply can
 * act on have an editable checkbox; EXACT rows start checked, NEAR rows
 * unchecked. Cancel, or closing the window, writes nothing.
 *
 * <p>When more than one VS Code build left a settings file, a combo names
 * which was read; choosing another reads it on the action's lane and
 * swaps the rows in.
 */
final class ImportVsCodeSettingsSheet {

    private ImportVsCodeSettingsSheet() {
    }

    /** The table over a plan's rows: Import, Setting, Value, In NMOX Studio. */
    static final class Model extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private transient List<Row> rows = List.of();
        private boolean[] checked = new boolean[0];

        Model(List<Row> rows) {
            replace(rows);
        }

        /** New rows; each starts checked when Apply can act on it exactly. */
        void replace(List<Row> next) {
            rows = List.copyOf(next);
            checked = new boolean[rows.size()];
            for (int i = 0; i < rows.size(); i++) {
                checked[i] = rows.get(i).applicable() && rows.get(i).fit() == Fit.EXACT;
            }
            fireTableDataChanged();
        }

        /** The rows Apply would write: checked and applicable, in order. */
        List<Row> chosen() {
            List<Row> out = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                if (checked[i] && rows.get(i).applicable()) {
                    out.add(rows.get(i));
                }
            }
            return out;
        }

        Row row(int i) {
            return i < 0 || i >= rows.size() ? null : rows.get(i);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return ImportVsCodeSettingsAction.message(switch (column) {
                case 0 -> "ImportVsCode_colImport";
                case 1 -> "ImportVsCode_colSetting";
                case 2 -> "ImportVsCode_colValue";
                default -> "ImportVsCode_colHere";
            });
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 && rows.get(row).applicable();
        }

        @Override
        public Object getValueAt(int row, int column) {
            Row r = rows.get(row);
            return switch (column) {
                case 0 -> checked[row];
                case 1 -> r.key();
                case 2 -> r.value();
                default -> r.here();
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0 && rows.get(row).applicable() && value instanceof Boolean b) {
                checked[row] = b;
                fireTableCellUpdated(row, column);
            }
        }
    }

    /**
     * What closing the sheet does: Apply writes the chosen rows into
     * {@code homes}; anything else (Cancel, the window's close box) writes
     * nothing and returns null.
     */
    static Outcome close(Object pressed, Object apply, Model model, VsCodeUserSettings.Homes homes) {
        if (pressed != apply) {
            return null;
        }
        return VsCodeUserSettings.apply(model.chosen(), homes);
    }

    /** The status-line sentence for what Apply did. */
    static String said(Outcome outcome) {
        String applied = ImportVsCodeSettingsAction.message("ImportVsCode_applied", outcome.applied().size());
        return outcome.failed().isEmpty() ? applied
                : ImportVsCodeSettingsAction.message("ImportVsCode_failed", applied, String.join(", ", outcome.failed()));
    }

    /** The heading: which file was read. */
    static String heading(Prepared prepared) {
        return ImportVsCodeSettingsAction.message("ImportVsCode_heading", prepared.read().build().label,
                prepared.read().file().toString());
    }

    /** The footer: how many settings have no place here, never which. */
    static String footer(Prepared prepared) {
        int others = prepared.plan() == null ? 0 : prepared.plan().others();
        return ImportVsCodeSettingsAction.message("ImportVsCode_others", others);
    }

    private static DefaultTableCellRenderer cell() {
        return PlainTables.plain(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                    boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                setToolTipText(PlainText.plain(value == null ? "" : value.toString()));
                return this;
            }
        });
    }

    static void show(Prepared first) {
        String title = ImportVsCodeSettingsAction.message("ImportVsCode_title");
        Model model = new Model(first.plan().rows());
        JTable table = PlainTables.disableHtml(new JTable(model));
        table.getAccessibleContext().setAccessibleName(ImportVsCodeSettingsAction.message("ImportVsCode_tableName"));
        table.getColumnModel().getColumn(1).setCellRenderer(TextDirection.keepLeftToRight(cell()));
        table.getColumnModel().getColumn(2).setCellRenderer(TextDirection.keepLeftToRight(cell()));
        table.getColumnModel().getColumn(3).setCellRenderer(cell());
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(470);

        JTextArea heading = PlainDialogs.plain(heading(first) + "\n"
                + ImportVsCodeSettingsAction.message("ImportVsCode_intro"), title);
        JTextArea foot = PlainDialogs.plain(footer(first), title);

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(heading, BorderLayout.CENTER);
        if (first.found().size() > 1) {
            JComboBox<Location> source = new JComboBox<>(first.found().toArray(Location[]::new));
            source.setSelectedItem(first.read());
            source.setRenderer(PlainTables.plain(new DefaultListCellRenderer() {
                private static final long serialVersionUID = 1L;

                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                        boolean selected, boolean focused) {
                    String label = value instanceof Location l ? l.build().label : "";
                    return super.getListCellRendererComponent(list, label, index, selected, focused);
                }
            }));
            source.getAccessibleContext().setAccessibleName(ImportVsCodeSettingsAction.message("ImportVsCode_sourceName"));
            JLabel label = new JLabel(PlainText.plain(ImportVsCodeSettingsAction.message("ImportVsCode_source")));
            label.setLabelFor(source);
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
            row.add(label);
            row.add(source);
            top.add(row, BorderLayout.PAGE_END);
            int[] generation = {0};
            source.addActionListener(e -> {
                if (!(source.getSelectedItem() instanceof Location chosen)) {
                    return;
                }
                int mine = ++generation[0];
                ImportVsCodeSettingsAction.prepareThen(chosen, next -> {
                    if (mine != generation[0]) {
                        return; // a newer choice is on its way
                    }
                    if (next.problem() != null && next.problem() != ImportVsCodeSettingsAction.Problem.NOTHING) {
                        model.replace(List.of());
                        heading.setText(ImportVsCodeSettingsAction.problem(next));
                        foot.setText("");
                        return;
                    }
                    model.replace(next.plan() == null ? List.of() : next.plan().rows());
                    heading.setText(heading(next) + "\n" + ImportVsCodeSettingsAction.message("ImportVsCode_intro"));
                    foot.setText(footer(next));
                });
            });
        }

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 0, 10));
        panel.add(top, BorderLayout.PAGE_START);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(900, Math.min(380, 60 + 20 * model.getRowCount())));
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(foot, BorderLayout.PAGE_END);

        JButton apply = new JButton(NbBundle.getMessage(ImportVsCodeSettingsAction.class, "ImportVsCode_apply"));
        JButton cancel = new JButton(NbBundle.getMessage(ImportVsCodeSettingsAction.class, "ImportVsCode_cancel"));
        DialogDescriptor descriptor = new DialogDescriptor(panel, title, true, new Object[] {apply, cancel}, cancel,
                DialogDescriptor.DEFAULT_ALIGN, null, null);
        descriptor.setClosingOptions(new Object[] {apply, cancel});
        Object pressed = DialogDisplayer.getDefault().notify(descriptor);
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        Outcome outcome = close(pressed, apply, model, ProductHomes.forProduct());
        if (outcome != null) {
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(said(outcome)));
        }
    }
}

package org.nmox.studio.ui.irc;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;

/**
 * The {@code /list} channel browser: a MODELESS dialog (the house
 * modeless-viewer blessing — browsing channels shouldn't lock the app)
 * over the rows a {@link ChannelListCollector} gathered. A filter field
 * narrows by name or topic, columns sort (users descending finds the
 * busy rooms), and a double-click joins the channel through the
 * callback the window supplied. When the collector hit its cap the
 * title says so — "showing first 2000 of N" — because a silently
 * truncated list is a lie.
 */
final class ChannelListDialog {

    private ChannelListDialog() {
    }

    /** Opens the browser; {@code join} is called with a channel name on double-click. */
    static void show(String network, List<ChannelListCollector.Row> rows,
            int totalSeen, Consumer<String> join) {
        Model model = new Model(rows);
        JTable table = org.nmox.studio.core.util.PlainTables.disableHtml(new JTable(model));
        table.getAccessibleContext().setAccessibleName("Channels");
        TableRowSorter<Model> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(0).setPreferredWidth(180);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(420);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
                    join.accept(rows.get(modelRow).name());
                }
            }
        });

        JTextField filter = new JTextField();
        filter.getAccessibleContext().setAccessibleName("Filter channels");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            private void refilter() {
                String q = filter.getText().trim().toLowerCase(Locale.ROOT);
                sorter.setRowFilter(q.isEmpty() ? null : new javax.swing.RowFilter<>() {
                    @Override
                    public boolean include(Entry<? extends Model, ? extends Integer> entry) {
                        ChannelListCollector.Row r = rows.get(entry.getIdentifier());
                        return r.name().toLowerCase(Locale.ROOT).contains(q)
                                || r.topic().toLowerCase(Locale.ROOT).contains(q);
                    }
                });
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                refilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refilter();
            }
        });

        String count = totalSeen > rows.size()
                ? "Showing first " + rows.size() + " of " + totalSeen + " channels"
                : rows.size() + " channels";
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(new JLabel("Filter:"), BorderLayout.WEST);
        top.add(filter, BorderLayout.CENTER);
        top.add(new JLabel(count), BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(720, 420));

        DialogDescriptor dd = new DialogDescriptor(panel,
                "Channels on " + network + " (double-click to join)");
        dd.setModal(false);
        dd.setOptions(new Object[] {DialogDescriptor.CLOSED_OPTION});
        DialogDisplayer.getDefault().createDialog(dd).setVisible(true);
    }

    /** Three read-only columns over the collector's rows. */
    private static final class Model extends AbstractTableModel {

        private final List<ChannelListCollector.Row> rows;

        Model(List<ChannelListCollector.Row> rows) {
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int c) {
            return switch (c) {
                case 0 -> "Channel";
                case 1 -> "Users";
                default -> "Topic";
            };
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 1 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int r, int c) {
            ChannelListCollector.Row row = rows.get(r);
            return switch (c) {
                case 0 -> row.name();
                case 1 -> row.users();
                default -> row.topic();
            };
        }
    }
}

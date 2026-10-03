package org.nmox.studio.tools.vscode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.core.util.PlainDialogs;
import org.nmox.studio.core.util.PlainTables;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.core.util.TextDirection;
import org.nmox.studio.tools.vscode.ExtensionEquivalents.Door;
import org.nmox.studio.tools.vscode.RecommendedExtensionsAction.Line;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.util.NbBundle;

/**
 * The VS Code extensions sheet: one row per recommended extension, its
 * id and what covers it here, with an Open button for the rows that
 * name a window. Thin on purpose — every word in it was decided by
 * {@link ExtensionEquivalents} and {@link RecommendedExtensionsAction}
 * before it got here.
 *
 * <p>The ids are a repository's text, so both columns paint through
 * {@link PlainTables} (a cell beginning {@code <html>} shows as those
 * characters), and the id column keeps left-to-right order in a
 * mirrored window: {@code dbaeumer.vscode-eslint} is machine text.
 *
 * <p>Not modal: Open brings a window forward and the sheet stays beside
 * it. Because it can outlive the aim it was read from, it listens for a
 * re-aim while it is showing and closes itself when the project changes
 * (a result belongs to the workspace that produced it); the listener
 * comes off when the sheet closes.
 */
final class RecommendedExtensionsSheet {

    private RecommendedExtensionsSheet() {
    }

    /** The table's two columns over the prepared rows; nothing is editable. */
    static final class Model extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final transient List<Line> lines;

        Model(List<Line> lines) {
            this.lines = List.copyOf(lines);
        }

        @Override
        public int getRowCount() {
            return lines.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return RecommendedExtensionsAction.message(column == 0 ? "VsCodeExtensions_colExtension"
                    : "VsCodeExtensions_colHere");
        }

        @Override
        public Object getValueAt(int row, int column) {
            Line line = lines.get(row);
            return column == 0 ? line.id() : line.here();
        }

        /** The row's line, or null for no row. */
        Line line(int row) {
            return row < 0 || row >= lines.size() ? null : lines.get(row);
        }
    }

    /** A plain-text cell whose tooltip is its whole text, for a sentence the column cuts. */
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

    /** The door a row's Open would run right now: its action resolved and enabled, else null. */
    static Action runnable(Line line, Map<Door, Action> doors) {
        if (line == null || line.opens() == null) {
            return null;
        }
        Action action = doors.get(line.opens());
        try {
            return action != null && action.isEnabled() ? action : null;
        } catch (RuntimeException | LinkageError broken) {
            return null;
        }
    }

    static void show(File root, String heading, List<Line> lines, Map<Door, Action> doors) {
        Model model = new Model(lines);
        JTable table = PlainTables.disableHtml(new JTable(model));
        table.getAccessibleContext().setAccessibleName(RecommendedExtensionsAction.message("VsCodeExtensions_tableName"));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setCellRenderer(TextDirection.keepLeftToRight(cell()));
        table.getColumnModel().getColumn(1).setCellRenderer(cell());
        table.getColumnModel().getColumn(0).setPreferredWidth(250);
        table.getColumnModel().getColumn(1).setPreferredWidth(510);

        String title = RecommendedExtensionsAction.message("VsCodeExtensions_title", root.getName());
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 0, 10));
        panel.add(PlainDialogs.plain(heading + "\n" + RecommendedExtensionsAction.message("VsCodeExtensions_intro"), title),
                BorderLayout.PAGE_START);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(760, Math.min(360, 60 + 18 * lines.size())));
        panel.add(scroll, BorderLayout.CENTER);

        JButton open = new JButton(NbBundle.getMessage(RecommendedExtensionsSheet.class, "VsCodeExtensions_open"));
        JButton close = new JButton(NbBundle.getMessage(RecommendedExtensionsSheet.class, "VsCodeExtensions_close"));
        open.setEnabled(false);
        Runnable openSelected = () -> {
            Action action = runnable(model.line(table.getSelectedRow()), doors);
            if (action != null) {
                action.actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, ""));
            }
        };
        table.getSelectionModel().addListSelectionListener(
                e -> open.setEnabled(runnable(model.line(table.getSelectedRow()), doors) != null));
        open.addActionListener(e -> openSelected.run());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected.run();
                }
            }
        });

        DialogDescriptor descriptor = new DialogDescriptor(panel, title, false, new Object[] {open, close}, close,
                DialogDescriptor.DEFAULT_ALIGN, null, null);
        descriptor.setClosingOptions(new Object[] {close});
        Dialog dialog = DialogDisplayer.getDefault().createDialog(descriptor);
        close.addActionListener(e -> dialog.dispose());

        ProjectAim aim = ProjectAim.find();
        if (aim != null) {
            ProjectAim.Listener reaimed = () -> java.awt.EventQueue.invokeLater(() -> {
                if (!root.equals(aim.projectDir())) {
                    dialog.dispose();
                }
            });
            aim.addListener(reaimed);
            // off on either way out: a closing option disposes the dialog,
            // and the title bar's close button may only hide it
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    aim.removeListener(reaimed);
                }

                @Override
                public void windowClosing(WindowEvent e) {
                    aim.removeListener(reaimed);
                }
            });
        }
        dialog.setVisible(true);
        if (model.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
    }
}

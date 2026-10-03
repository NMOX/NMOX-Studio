package org.nmox.studio.tools.vscode;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.OptionalInt;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import org.nmox.studio.core.util.PlainTables;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;

/**
 * The list a menu door shows when a repository's {@code .vscode} file
 * offers several things to start: the tasks of {@code tasks.json} for
 * <i>Run ▸ Run Task…</i>, the configurations of {@code launch.json} for
 * <i>Debug ▸ Start Debugging…</i>. One list, the first row chosen, Enter
 * (or a double-click) starts the chosen row, Escape starts nothing.
 *
 * <p>Every row is the repository's own text — a label and a command — so
 * the renderer shows it as text, never markup (the v1.208.0 law), and the
 * list is named for assistive technology by the dialog's title. Typing
 * jumps to the row that starts with what was typed: Swing's own list
 * type-ahead, which is the picker VS Code's list is, without a second
 * field to keep in step.
 */
final class VsCodePicker {

    private VsCodePicker() {
    }

    /**
     * Shows {@code rows} and waits for a choice. Event thread only.
     *
     * @param title   the dialog's title, and the list's accessible name
     * @param start   the default button's label (<i>Run</i>, <i>Debug</i>)
     * @param rows    one line per choice, already one line each
     * @return the chosen row's index, or empty for Cancel
     */
    static OptionalInt pick(String title, String start, List<String> rows) {
        JList<String> list = new JList<>(rows.toArray(String[]::new));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(PlainTables.plain(new DefaultListCellRenderer()));
        list.setVisibleRowCount(Math.min(12, Math.max(4, rows.size())));
        list.setSelectedIndex(0);
        list.getAccessibleContext().setAccessibleName(title);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(520, scroll.getPreferredSize().height));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 4, 10));
        panel.add(scroll, BorderLayout.CENTER);

        JButton go = new JButton(org.nmox.studio.core.util.PlainText.plain(start));
        DialogDescriptor descriptor = new DialogDescriptor(panel, title, true,
                new Object[] {go, DialogDescriptor.CANCEL_OPTION}, go,
                DialogDescriptor.DEFAULT_ALIGN, null, null);
        java.awt.Dialog dialog = DialogDisplayer.getDefault().createDialog(descriptor);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.locationToIndex(e.getPoint()) >= 0) {
                    go.doClick();
                }
            }
        });
        dialog.setVisible(true); // modal: returns when the dialog is closed
        dialog.dispose();
        return descriptor.getValue() == go && list.getSelectedIndex() >= 0
                ? OptionalInt.of(list.getSelectedIndex()) : OptionalInt.empty();
    }
}

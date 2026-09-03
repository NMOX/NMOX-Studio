package org.nmox.studio.ui.shortcuts;

import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.nmox.studio.core.util.PlainTables;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;

/**
 * Help ▸ Keyboard Shortcuts… — the sheet derived from the RUNNING keymap:
 * every NMOX shadow in the active profile's Keymaps folder, resolved to
 * its action's display name through the platform (the same object the
 * menu shows), rendered with the notation law in {@link ShortcutSheet}.
 * Nothing hand-kept, so the sheet cannot drift from the product. Editor
 * kit chords (Emmet, Go to Declaration in templates) live in the editors'
 * keybindings, not the Keymaps folder, and the dialog says so.
 */
@ActionID(category = "Help", id = "org.nmox.studio.ui.shortcuts.KeyboardShortcutsAction")
@ActionRegistration(displayName = "#CTL_KeyboardShortcutsAction", lazy = true)
@ActionReference(path = "Menu/Help", position = 228)
@Messages("CTL_KeyboardShortcutsAction=Keyboard Shortcuts…")
public final class KeyboardShortcutsAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        // on the EDT on purpose (v2.64.1 review): the system filesystem is
        // the in-memory layer cache, and resolving lazy actions belongs on
        // the thread that will present them — no disk, no process
        String profile = activeProfile();
        dialog(profile, rows(profile));
    }

    /** The profile the platform marks current on the Keymaps folder, default NetBeans. */
    static String activeProfile() {
        FileObject keymaps = FileUtil.getConfigFile("Keymaps");
        Object current = keymaps == null ? null : keymaps.getAttribute("currentKeymap");
        return current instanceof String s && !s.isBlank() ? s : "NetBeans";
    }

    /** Every NMOX-owned shadow in the profile, resolved to the action the platform builds. */
    static List<ShortcutSheet.Row> rows(String profile) {
        List<ShortcutSheet.Row> rows = new ArrayList<>();
        FileObject folder = FileUtil.getConfigFile("Keymaps/" + profile);
        if (folder == null) {
            return rows;
        }
        boolean mac = Utilities.isMac();
        for (FileObject shadow : folder.getChildren()) {
            if (!"shadow".equals(shadow.getExt())) {
                continue;
            }
            Object original = shadow.getAttribute("originalFile");
            if (!(original instanceof String path) || !path.contains("org-nmox-")) {
                continue;
            }
            Action a = FileUtil.getConfigObject(path, Action.class);
            Object name = a == null ? null : a.getValue(Action.NAME);
            String label = name == null ? path.substring(path.lastIndexOf('/') + 1) : name.toString().replace("&", "");
            rows.add(new ShortcutSheet.Row(ShortcutSheet.humanChord(shadow.getName(), mac), label));
        }
        return ShortcutSheet.sorted(rows);
    }

    private static void dialog(String profile, List<ShortcutSheet.Row> rows) {
        String[] cols = {"Shortcut", "Action"};
        Object[][] data = new Object[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            data[i] = new Object[]{rows.get(i).chord(), rows.get(i).action()};
        }
        JTable table = PlainTables.disableHtml(new JTable(data, cols) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        });
        table.getAccessibleContext().setAccessibleName("Keyboard shortcuts");
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(420);
        JPanel panel = new JPanel(new java.awt.BorderLayout(0, 6));
        panel.add(new JLabel(rows.size() + " NMOX shortcuts in the " + profile + " keymap profile. "
                + "Editor-kit chords (Emmet ⌥⌘E, template Go to Declaration ⌘B) are in the user guide."),
                java.awt.BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new java.awt.Dimension(560, 380));
        panel.add(scroll, java.awt.BorderLayout.CENTER);
        Object copy = "Copy as Markdown";
        Object close = "Close";
        NotifyDescriptor nd = new NotifyDescriptor(panel, "Keyboard Shortcuts", NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.PLAIN_MESSAGE, new Object[]{copy, close}, close);
        if (DialogDisplayer.getDefault().notify(nd) == copy) {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(ShortcutSheet.renderMarkdown(rows, profile)), null);
            StatusDisplayer.getDefault().setStatusText("Shortcut sheet copied as Markdown.");
        }
    }
}

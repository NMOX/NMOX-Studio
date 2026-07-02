package org.nmox.studio.ui.actions;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * The daily-driver project switcher: Cmd+Shift+P pops the recent
 * projects, type to narrow, Enter re-aims the whole IDE. The switch
 * goes through {@link RackService#openProject}, which guards work in
 * flight - a running dev server is named and confirmed, never silently
 * killed by the patch swap.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.SwitchProjectAction")
@ActionRegistration(displayName = "#CTL_SwitchProjectAction")
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 65),
    @ActionReference(path = "Shortcuts", name = "DS-P")
})
@Messages("CTL_SwitchProjectAction=Switch Project...")
public final class SwitchProjectAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        List<File> recents = RackService.getDefault().getRecentProjects();
        if (recents.isEmpty()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "No recent projects yet — open a folder or create a project first."));
            return;
        }

        DefaultListModel<File> model = new DefaultListModel<>();
        recents.forEach(model::addElement);
        JList<File> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setVisibleRowCount(Math.min(10, recents.size()));
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object value,
                    int index, boolean selected, boolean focus) {
                File dir = (File) value;
                super.getListCellRendererComponent(l,
                        dir.getName() + "  —  " + dir.getAbsolutePath(), index, selected, focus);
                return this;
            }
        });

        JTextField filter = new JTextField();
        filter.getDocument().addDocumentListener(new DocumentListener() {
            private void refilter() {
                String needle = filter.getText().toLowerCase();
                model.clear();
                for (File dir : recents) {
                    if (dir.getName().toLowerCase().contains(needle)
                            || dir.getAbsolutePath().toLowerCase().contains(needle)) {
                        model.addElement(dir);
                    }
                }
                if (!model.isEmpty()) {
                    list.setSelectedIndex(0);
                }
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
        // arrows steer the list without leaving the filter field
        filter.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent ev) {
                int i = list.getSelectedIndex();
                if (ev.getKeyCode() == KeyEvent.VK_DOWN && i < model.size() - 1) {
                    list.setSelectedIndex(i + 1);
                    ev.consume();
                } else if (ev.getKeyCode() == KeyEvent.VK_UP && i > 0) {
                    list.setSelectedIndex(i - 1);
                    ev.consume();
                }
                list.ensureIndexIsVisible(list.getSelectedIndex());
            }
        });

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(filter, BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.setPreferredSize(new java.awt.Dimension(520, 280));

        DialogDescriptor descriptor = new DialogDescriptor(panel, "Switch Project");
        java.awt.Dialog[] dialog = new java.awt.Dialog[1];
        Runnable open = () -> {
            File chosen = list.getSelectedValue();
            if (chosen != null && chosen.isDirectory()) {
                dialog[0].setVisible(false);
                switchTo(chosen);
            }
        };
        filter.addActionListener(ev -> open.run());
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent ev) {
                if (ev.getClickCount() == 2) {
                    open.run();
                }
            }
        });

        dialog[0] = DialogDisplayer.getDefault().createDialog(descriptor);
        filter.requestFocusInWindow();
        dialog[0].setVisible(true);
        if (descriptor.getValue() == DialogDescriptor.OK_OPTION) {
            File chosen = list.getSelectedValue();
            if (chosen != null && chosen.isDirectory()) {
                switchTo(chosen);
            }
        }
        dialog[0].dispose();
    }

    private void switchTo(File dir) {
        RackService.getDefault().openProject(dir);
        TopComponent workbench = WindowManager.getDefault()
                .findTopComponent("ProjectExplorerTopComponent");
        if (workbench != null) {
            workbench.open();
            workbench.requestActive();
        }
    }
}

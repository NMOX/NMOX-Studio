package org.nmox.studio.ui.actions;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.nmox.studio.rack.projectstudio.EnvironmentDoctor;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Environment Doctor: one honest table of every external tool the
 * studio leans on — the core four, the language toolchains, and every
 * learning-space interpreter — probed live (never guessed from a
 * cache) with its version or the install command that would fix it.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.ui.actions.EnvironmentDoctorAction")
@ActionRegistration(displayName = "#CTL_EnvironmentDoctorAction")
@ActionReference(path = "Menu/Tools", position = 90)
@Messages("CTL_EnvironmentDoctorAction=Environment Doctor...")
public final class EnvironmentDoctorAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"", "Tool", "Status", "Used for", "Install"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setMaxWidth(28);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(230);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.getColumnModel().getColumn(4).setPreferredWidth(200);

        JLabel status = new JLabel("Probing…");
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Every tool the studio can drive, probed live on this machine:"),
                BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(780, 480));

        List<String[]> checks = EnvironmentDoctor.checklist();
        // probes run off the EDT and stream into the table as they land
        RequestProcessor.getDefault().post(() -> {
            int found = 0;
            for (String[] check : checks) {
                EnvironmentDoctor.Finding f =
                        EnvironmentDoctor.probe(check[0], check[1], check[2]);
                if (f.found()) {
                    found++;
                }
                int soFar = found;
                int done = model.getRowCount() + 1;
                SwingUtilities.invokeLater(() -> {
                    model.addRow(new Object[]{f.found() ? "✓" : "✗", f.tool(),
                        f.detail(), f.purpose(), f.found() ? "" : f.installHint()});
                    status.setText(done < checks.size()
                            ? "Probing…  " + done + "/" + checks.size()
                            : soFar + " of " + checks.size() + " tools present");
                });
            }
        });

        DialogDescriptor descriptor = new DialogDescriptor(panel, "Environment Doctor",
                false, new Object[]{DialogDescriptor.CLOSED_OPTION}, null, 0, null, null);
        DialogDisplayer.getDefault().createDialog(descriptor).setVisible(true);
    }
}

package org.nmox.studio.ui.actions;

import org.nmox.studio.core.util.PlainTables;
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
@Messages("CTL_EnvironmentDoctorAction=Environment Doctor…")
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
        JTable table = org.nmox.studio.core.util.PlainTables.disableHtml(new JTable(model));
        table.getAccessibleContext().setAccessibleName("Environment Doctor probes");
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setMaxWidth(28);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(230);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.getColumnModel().getColumn(4).setPreferredWidth(200);

        JLabel status = PlainTables.plain(new JLabel("Probing…"));
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Every tool the studio can drive, probed live on this machine:"),
                BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(780, 480));

        List<String[]> checks = EnvironmentDoctor.checklist();
        // probes run off the EDT and stream into the table as they land;
        // the drop-in scan (~/.nmox/doctor.d, v1.305.0) is file IO and
        // rides the same lane — the dir is read fresh on every open
        RequestProcessor.getDefault().post(() -> {
            java.util.Set<String> taken = new java.util.HashSet<>();
            for (String[] check : checks) {
                taken.add(check[0]);
            }
            org.nmox.studio.rack.projectstudio.UserProbes.Loaded yours =
                    org.nmox.studio.rack.projectstudio.UserProbes.load(taken);
            int total = checks.size() + yours.probes().size();
            List<EnvironmentDoctor.Finding> findings = new java.util.ArrayList<>();
            for (String[] check : checks) {
                findings.add(EnvironmentDoctor.probe(check[0], check[1], check[2]));
                publish(model, status, findings, yours.skipped(), total);
            }
            for (org.nmox.studio.rack.projectstudio.UserProbes.Custom custom
                    : yours.probes()) {
                findings.add(EnvironmentDoctor.probeCustom(custom));
                publish(model, status, findings, yours.skipped(), total);
            }
        });

        DialogDescriptor descriptor = new DialogDescriptor(panel, "Environment Doctor",
                false, new Object[]{DialogDescriptor.CLOSED_OPTION}, null, 0, null, null);
        DialogDisplayer.getDefault().createDialog(descriptor).setVisible(true);
    }

    /**
     * Streams the newest finding into the table; on the LAST one, appends
     * a visible row per skipped drop-in file (the family's skip-with-note
     * law: a refused probe is said where the user is looking, never a
     * silent absence) and settles the status line.
     */
    private static void publish(DefaultTableModel model, JLabel status,
            List<EnvironmentDoctor.Finding> findings, List<String> skipped,
            int total) {
        EnvironmentDoctor.Finding f = findings.get(findings.size() - 1);
        int done = findings.size();
        long found = findings.stream()
                .filter(EnvironmentDoctor.Finding::found).count();
        SwingUtilities.invokeLater(() -> {
            model.addRow(new Object[]{f.found() ? "✓" : "✗", f.tool(),
                f.detail(), f.purpose(), f.found() ? "" : f.installHint()});
            if (done < total) {
                status.setText("Probing…  " + done + "/" + total);
            } else {
                for (String note : skipped) {
                    model.addRow(new Object[]{"—", "doctor.d",
                        "skipped — " + note, "", ""});
                }
                status.setText(found + " of " + total + " tools present");
            }
        });
    }
}

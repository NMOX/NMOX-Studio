package org.nmox.studio.ui.actions;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.nmox.studio.rack.projectstudio.ContractKit;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * The Contract Kit wizard: pick any chain the kit speaks (the list IS
 * {@link ContractKit.Chain} — ten as of v1.151.0, from Solidity/Foundry
 * through Bitcoin/Miniscript, Clarity/Stacks, and Cardano/Aiken), name the contract, and
 * the kit scaffolds the arc's live-proven starter into the aimed project:
 * manifest, contract, native test, and a CONTRACT-NOTES.md naming the
 * rack devices and one-time steps. Never clobbers; no keys, ever.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.ContractKitAction")
@ActionRegistration(displayName = "#CTL_ContractKitAction")
@ActionReference(path = "Menu/File", position = 120)
@Messages("CTL_ContractKitAction=Contract Kit (Web3)...")
public final class ContractKitAction implements ActionListener {

    /** True when the chain's tool resolves on the augmented PATH. */
    static boolean toolOnPath(String tool) {
        return !org.nmox.studio.core.process.ToolLocator.resolve(tool).equals(tool);
    }

    /** The report surface, Standards Kit style: ✓ changed, – left alone. */
    static String renderReport(List<ContractKit.Outcome> outcomes) {
        StringBuilder report = new StringBuilder();
        for (ContractKit.Outcome o : outcomes) {
            report.append(o.changed() ? "  ✓ " : "  – ").append(o.path());
            if (!"written".equals(o.status())) {
                report.append("  (").append(o.status()).append(')');
            }
            report.append('\n');
        }
        return report.toString();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Aim the studio at a project first (open a folder or project)."));
            return;
        }

        JComboBox<ContractKit.Chain> chains = new JComboBox<>(ContractKit.Chain.values());
        chains.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l,
                    Object v, int i, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l,
                        v instanceof ContractKit.Chain c ? c.label : v, i, sel, focus);
            }
        });
        JTextField name = new JTextField("MyContract");
        name.selectAll();

        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Chain:"));
        panel.add(chains);
        panel.add(new JLabel("Contract name (an identifier — cased per the chain's convention):"));
        panel.add(name);
        panel.add(new JLabel("<html><small>Scaffolds the live-proven starter — manifest, contract, "
                + "native test, and next-step notes. Existing files are never overwritten; "
                + "a differing file gets a .suggested sibling. Keys never touch the IDE.</small></html>"));

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                "Contract Kit — " + project.getName());
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }

        ContractKit.Chain chain = (ContractKit.Chain) chains.getSelectedItem();
        String contractName = name.getText().trim();
        String invalid = ContractKit.validate(contractName);
        if (invalid != null) {
            SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                    new NotifyDescriptor.Message(invalid, NotifyDescriptor.WARNING_MESSAGE)));
            return;
        }
        // disk I/O has no place in an event dispatch; the report then hops
        // back to a fresh EDT dispatch so it can't stack behind the main window
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            try {
                List<ContractKit.Outcome> outcomes =
                        ContractKit.scaffold(project, chain, contractName);
                String report = renderReport(outcomes);
                String hint = toolOnPath(chain.tool) ? ""
                        : "\n\nHeads up: `" + chain.tool + "` isn't on your PATH yet — "
                          + "CONTRACT-NOTES.md has the install line.";
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message("Contract Kit (" + chain.label + "):\n\n"
                                + report + "\nCONTRACT-NOTES.md has the next steps." + hint,
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                String message = "Could not scaffold: " + ex.getMessage();
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}

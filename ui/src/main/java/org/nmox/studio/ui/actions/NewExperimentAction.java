package org.nmox.studio.ui.actions;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.nmox.studio.rack.projectstudio.Experiments;
import org.nmox.studio.rack.projectstudio.ProjectTemplates;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * New Experiment: a throwaway workspace in two clicks. Pick a template,
 * optionally name it, and the studio generates it under
 * ~/.nmox/experiments - no git, no recents pollution, pre-trusted -
 * and aims the rack at it. Keepers graduate via Experiments.promote;
 * the rest die without ceremony.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.NewExperimentAction")
@ActionRegistration(displayName = "#CTL_NewExperimentAction")
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 115),
    @ActionReference(path = "Shortcuts", name = "DS-E")
})
@Messages("CTL_NewExperimentAction=New Experiment...")
public final class NewExperimentAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        JComboBox<ProjectTemplates> template = new JComboBox<>(ProjectTemplates.values());
        template.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l,
                    Object v, int i, boolean s, boolean f) {
                ProjectTemplates t = (ProjectTemplates) v;
                return super.getListCellRendererComponent(l,
                        t.getDisplayName() + "  —  " + t.getDescription(), i, s, f);
            }
        });
        JTextField name = new JTextField();
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel rows = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        rows.add(new JLabel("Template:"));
        rows.add(template);
        rows.add(new JLabel("Name (optional — a throwaway name is fine):"));
        rows.add(name);
        rows.add(new JLabel("<html><small>Lives in ~/.nmox/experiments — no git, no recents. "
                + "Promote it later if it turns into something.</small></html>"));
        panel.add(rows, BorderLayout.CENTER);

        DialogDescriptor descriptor = new DialogDescriptor(panel, "New Experiment");
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }
        try {
            File dir = Experiments.create(
                    (ProjectTemplates) template.getSelectedItem(), name.getText());
            RackService.getDefault().openProjectQuietly(dir);
            org.openide.windows.TopComponent workbench = org.openide.windows.WindowManager
                    .getDefault().findTopComponent("ProjectExplorerTopComponent");
            if (workbench != null) {
                workbench.open();
                workbench.requestActive();
            }
        } catch (Exception ex) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Could not create the experiment: " + ex.getMessage(),
                    NotifyDescriptor.ERROR_MESSAGE));
        }
    }
}

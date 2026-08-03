package org.nmox.studio.ui.actions;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.nmox.studio.rack.service.RackService;
import org.nmox.studio.rack.service.WorkspaceTrust;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * File ▸ New Angular Schematic… (v1.239.0, the Angular bet): the
 * terminal habit — cd into a folder, {@code ng g c widget} — as an
 * IDE gesture. Pick a schematic and a name, point it at a folder
 * inside the aimed Angular workspace, and the CLI generates in place
 * with its output streaming to the Output window; the Project Studio
 * tree picks the new files up through the normal refresh.
 *
 * <p>The kit-action idiom (Image Kit, Classic Kit): always enabled,
 * honest dialog refusal when the aim isn't an Angular workspace —
 * enablement that walks the disk on menu paint is the v1.114.0 bug
 * class, and HALO's GEN button already serves the rack-side of this
 * gesture with the SAME schematic vocabulary.
 *
 * <p>Trust law: ng and the project's schematics execute the repo's
 * own code (angular.json builders, node_modules), so the spawn sits
 * behind {@code WorkspaceTrust.requestTrust} — classified GATED in
 * the spawn-site ledger.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.NgSchematicAction")
@ActionRegistration(displayName = "#CTL_NgSchematicAction")
@ActionReference(path = "Menu/File", position = 122)
@Messages("CTL_NgSchematicAction=New Angular Schematic...")
public final class NgSchematicAction implements ActionListener {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-ng-schematic", 1);

    @Override
    public void actionPerformed(ActionEvent e) {
        File aim = RackService.getDefault().getRack().getProjectDir();
        File root = NgSchematic.angularRoot(aim);
        if (root == null) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Aim the studio at an Angular workspace first — this dialog "
                    + "drives `ng generate`, and it needs the project's own "
                    + "angular.json (the rack's HALO device carries the same "
                    + "GEN control)."));
            return;
        }

        JComboBox<String> type = new JComboBox<>(NgSchematic.SCHEMATICS);
        JTextField name = new JTextField("widget", 24);
        // ng resolves against its cwd, so the folder field IS the target
        JTextField folder = new JTextField(
                new File(root, "src/app").isDirectory() ? "src/app" : "", 24);
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
        panel.add(new JLabel("Schematic:"));
        panel.add(type);
        panel.add(new JLabel("Name:"));
        panel.add(name);
        panel.add(new JLabel("In folder (under the workspace):"));
        panel.add(folder);

        DialogDescriptor dd = new DialogDescriptor(panel,
                "New Angular Schematic — " + root.getName());
        if (DialogDisplayer.getDefault().notify(dd) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String schematic = (String) type.getSelectedItem();
        String rawName = name.getText();
        if (!NgSchematic.validName(rawName)) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Name must be a single identifier — no spaces or slashes "
                    + "(the folder field owns placement)."));
            return;
        }
        File target = NgSchematic.targetFolder(root, folder.getText());
        if (target == null) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "That folder doesn't exist inside the workspace — pick a "
                    + "directory under " + root.getName() + '.'));
            return;
        }

        // trust prompt on the EDT, spawn on the RP (the v1.57.0 split)
        RP.post(() -> {
            if (!WorkspaceTrust.requestTrust(root)) {
                return; // Keep Safe: no spawn, no output tab
            }
            // the empty line consumer is DELIBERATE (v1.212.0 lesson noted,
            // not repeated): a generate has no serving URL to announce and
            // no chain waiting on its lines — the Output tab itself is fed
            // by CommandExecutor regardless
            CommandExecutor.run("Angular: generate " + schematic + " — " + rawName.trim(),
                    target, Map.of(), NgSchematic.argv(schematic, rawName),
                    line -> {
                    },
                    exit -> java.awt.EventQueue.invokeLater(() ->
                            StatusDisplayer.getDefault().setStatusText(exit == 0
                                    ? "ng generate " + schematic + " " + rawName.trim() + " — done."
                                    : "ng generate failed (exit " + exit + ") — see Output.")));
        });
    }
}

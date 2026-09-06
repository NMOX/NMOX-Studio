package org.nmox.studio.rack.projectstudio;

import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.core.util.PlainTables;
import org.nmox.studio.core.spi.LiveRuns;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

/**
 * Project configuration: structured package.json editing. Identity
 * fields and the scripts table write the file directly; dependency
 * changes run through npm so the lockfile stays correct.
 */
public class ProjectConfigDialog extends JDialog {

    private final File projectDir;
    private PackageJsonFile pkg;

    private final JTextField nameField = new JTextField(24);
    private final JTextField versionField = new JTextField(10);
    private final JTextField descriptionField = new JTextField(32);
    private final JTextField licenseField = new JTextField(10);
    private final JComboBox<String> typeCombo = new JComboBox<>(new String[]{"commonjs", "module"});
    {
        // the form's labels also setLabelFor these (below); the explicit
        // names keep the law readable to the gate that enforces it (v2.85.0)
        nameField.getAccessibleContext().setAccessibleName("Name");
        versionField.getAccessibleContext().setAccessibleName("Version");
        descriptionField.getAccessibleContext().setAccessibleName("Description");
        licenseField.getAccessibleContext().setAccessibleName("License");
        typeCombo.getAccessibleContext().setAccessibleName("Module type");
    }
    private final DefaultTableModel scriptsModel = new DefaultTableModel(new Object[]{"Script", "Command"}, 0);
    private JTable scriptsTable;
    private final DefaultTableModel depsModel = new DefaultTableModel(new Object[]{"Package", "Version", "Scope"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    public ProjectConfigDialog(Component parent, File projectDir) throws IOException {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent), "Project Configuration",
                ModalityType.APPLICATION_MODAL);
        this.projectDir = projectDir;
        this.pkg = PackageJsonFile.load(projectDir);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Project", buildIdentityTab());
        tabs.addTab("Scripts", buildScriptsTab());
        tabs.addTab("Dependencies", buildDependenciesTab());

        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            if (saveAll()) {
                dispose();
            }
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(save);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        loadFields();
        setSize(640, 460);
        setLocationRelativeTo(parent);
    }

    private JPanel buildIdentityTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        for (Object[] pair : new Object[][]{
            {"Name:", nameField}, {"Version:", versionField},
            {"Description:", descriptionField}, {"License:", licenseField},
            {"Module type:", typeCombo}}) {
            c.gridx = 0;
            c.gridy = row;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            JLabel label = new JLabel(PlainText.plain((String) pair[0]));
            label.setLabelFor((Component) pair[1]); // the label names its field for assistive technology
            panel.add(label, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            panel.add((Component) pair[1], c);
            row++;
        }
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.weighty = 1;
        panel.add(new JLabel(" "), c);
        return panel;
    }

    private JPanel buildScriptsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JTable table = org.nmox.studio.core.util.PlainTables.disableHtml(new JTable(scriptsModel));
        table.getAccessibleContext().setAccessibleName("Scripts");
        scriptsTable = table;
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton addBtn = new JButton("Add Script");
        addBtn.addActionListener(e -> scriptsModel.addRow(new Object[]{"new-script", ""}));
        JButton removeBtn = new JButton("Remove");
        removeBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                if (table.isEditing()) {
                    table.getCellEditor().stopCellEditing();
                }
                scriptsModel.removeRow(row);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(addBtn);
        buttons.add(removeBtn);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildDependenciesTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JTable table = org.nmox.studio.core.util.PlainTables.disableHtml(new JTable(depsModel));
            table.getAccessibleContext().setAccessibleName("Dependencies");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton addBtn = new JButton("Add…");
        addBtn.setToolTipText("npm install <package> (Alt: choose dev scope in the prompt)");
        addBtn.addActionListener(e -> {
            JTextField pkgField = new JTextField(20);
            pkgField.getAccessibleContext().setAccessibleName("Package");
            JComboBox<String> scope = new JComboBox<>(new String[]{"dependency", "devDependency"});
            scope.getAccessibleContext().setAccessibleName("Scope");
            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2, 4, 2, 4);
            c.gridx = 0;
            c.gridy = 0;
            form.add(new JLabel("Package:"), c);
            c.gridx = 1;
            form.add(pkgField, c);
            c.gridx = 0;
            c.gridy = 1;
            form.add(new JLabel("Scope:"), c);
            c.gridx = 1;
            form.add(scope, c);
            DialogDescriptor dd = new DialogDescriptor(form, "Add Dependency");
            if (DialogDisplayer.getDefault().notify(dd) != DialogDescriptor.OK_OPTION) {
                return;
            }
            String name = pkgField.getText().trim();
            if (name.isEmpty()) {
                return;
            }
            boolean dev = scope.getSelectedIndex() == 1;
            // the project's own manager, not a hardcoded npm: writing a
            // package-lock.json into a pnpm/yarn tree desynchronizes the
            // lockfile (v1.212.0; the v1.60.0 law every other Node lane
            // already honors)
            String mgr = org.nmox.studio.rack.devices.ProjectInspector
                    .nodePackageManager(projectDir);
            runPackageManager(org.nmox.studio.rack.devices.NodePackageCommands
                    .add(mgr, name, dev));
        });

        JButton removeBtn = new JButton("Remove");
        removeBtn.setToolTipText("Remove the selected package with this project's package manager");
        removeBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                return;
            }
            String name = (String) depsModel.getValueAt(row, 0);
            String mgr = org.nmox.studio.rack.devices.ProjectInspector
                    .nodePackageManager(projectDir);
            List<String> argv = org.nmox.studio.rack.devices.NodePackageCommands
                    .remove(mgr, name);
            // the prompt names the command that will actually run, so the
            // confirmation is honest on a yarn/pnpm project too; full ctor
            // with NO as the initial value — a reflexive Enter must not
            // remove a dependency (v1.98.0)
            if (DialogDisplayer.getDefault().notify(new NotifyDescriptor(
                    org.nmox.studio.core.util.PlainDialogs.plain(org.nmox.studio.rack.devices.NodePackageCommands.describe(argv) + "?", "Message"),
                    "Remove Dependency",
                    NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.QUESTION_MESSAGE,
                    null, NotifyDescriptor.NO_OPTION)) == NotifyDescriptor.YES_OPTION) {
                runPackageManager(argv);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(addBtn);
        buttons.add(removeBtn);
        buttons.add(new JLabel("Changes run your package manager and refresh when it finishes."));
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    /** Runs an npm mutation and reloads the document when it exits. */
    private void runPackageManager(List<String> command) {
        // npm/pnpm/yarn install runs the project's OWN lifecycle scripts
        // (preinstall/postinstall, plus every dependency's) — an inward
        // execution flow like Run/F6, and gated the same way (v1.224.0
        // spawn-site sweep; prompt-once, a prior grant answers silently)
        if (!org.nmox.studio.rack.service.WorkspaceTrust.requestTrust(projectDir)) {
            return;
        }
        // the mutation joins the toolbar ■ (v2.71.0) — an add/remove is an
        // install under the hood, and a hung one had no stop on screen
        String runLabel = String.join(" ", command) + " — " + projectDir.getName();
        String runId = "project-config:" + projectDir.getAbsolutePath() + "#" + System.nanoTime();
        CommandExecutor.Handle handle = CommandExecutor.run("Project Config", projectDir, Map.of(), command,
                line -> {
                }, code -> javax.swing.SwingUtilities.invokeLater(() -> {
                    LiveRuns.remove(runId);
                    try {
                        pkg = PackageJsonFile.load(projectDir);
                        loadFields();
                    } catch (IOException ex) {
                        error("Could not reload package.json: " + ex.getMessage());
                    }
                    if (code != 0) {
                        warn(command.get(0) + " exited with " + code
                                + " — see the \"Rack: Project Config\" output tab.");
                    }
                }));
        LiveRuns.add(new LiveRuns.Run(runId, runLabel, handle::kill));
    }

    private void loadFields() {
        nameField.setText(pkg.getName());
        versionField.setText(pkg.getVersion());
        descriptionField.setText(pkg.getDescription());
        licenseField.setText(pkg.getLicense());
        typeCombo.setSelectedItem(pkg.getType());

        scriptsModel.setRowCount(0);
        pkg.getScripts().forEach((k, v) -> scriptsModel.addRow(new Object[]{k, v}));

        depsModel.setRowCount(0);
        pkg.getDependencies().forEach((k, v) -> depsModel.addRow(new Object[]{k, v, "runtime"}));
        pkg.getDevDependencies().forEach((k, v) -> depsModel.addRow(new Object[]{k, v, "dev"}));
    }

    /**
     * Trimmed script name that appears on more than one row, or null.
     *
     * <p>package.json scripts are a JSON OBJECT: two rows with one name
     * cannot both survive serialization, and the map used to fold rows
     * kept the LAST one — so renaming a script onto an existing name
     * silently REPLACED that script (v1.284.0, the project-starter
     * walk: renaming test→dev destroyed the running dev script AND the
     * test script in one Save, three scripts in, two out). Same class
     * as the v1.268.0 Block Studio tag collision: a rename that allows
     * a collision is data loss at serialization time. Blank rows are
     * skipped here because saveAll drops them anyway.
     */
    static String duplicateScriptName(javax.swing.table.TableModel m) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < m.getRowCount(); i++) {
            String key = String.valueOf(m.getValueAt(i, 0)).trim();
            String value = String.valueOf(m.getValueAt(i, 1)).trim();
            if (!key.isEmpty() && !value.isEmpty() && !seen.add(key)) {
                return key;
            }
        }
        return null;
    }

    private boolean saveAll() {
        // a half-typed cell that never saw Enter is still in its editor;
        // committing it here means Save saves what the user can SEE
        if (scriptsTable != null && scriptsTable.isEditing()) {
            scriptsTable.getCellEditor().stopCellEditing();
        }
        String dup = duplicateScriptName(scriptsModel);
        if (dup != null) {
            error("Two scripts are both named \"" + dup + "\" — script"
                    + " names must be unique, or one of them would be"
                    + " silently lost. Rename one and save again.");
            return false;
        }
        pkg.setName(nameField.getText());
        pkg.setVersion(versionField.getText());
        pkg.setDescription(descriptionField.getText());
        pkg.setLicense(licenseField.getText());
        pkg.setType((String) typeCombo.getSelectedItem());

        Map<String, String> scripts = new LinkedHashMap<>();
        for (int i = 0; i < scriptsModel.getRowCount(); i++) {
            String key = String.valueOf(scriptsModel.getValueAt(i, 0)).trim();
            String value = String.valueOf(scriptsModel.getValueAt(i, 1)).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                scripts.put(key, value);
            }
        }
        pkg.setScripts(scripts);

        try {
            pkg.save();
            return true;
        } catch (IOException ex) {
            error("Could not save package.json: " + ex.getMessage());
            return false;
        }
    }

    // ---- platform dialogs (parented, keyboard-correct, consistent chrome) ----

    private void error(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                org.nmox.studio.core.util.PlainDialogs.plain(message, "Message"), NotifyDescriptor.ERROR_MESSAGE));
    }

    private void warn(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                org.nmox.studio.core.util.PlainDialogs.plain(message, "Message"), NotifyDescriptor.WARNING_MESSAGE));
    }
}

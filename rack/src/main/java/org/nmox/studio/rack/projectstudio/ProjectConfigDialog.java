package org.nmox.studio.rack.projectstudio;

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
    private final DefaultTableModel scriptsModel = new DefaultTableModel(new Object[]{"Script", "Command"}, 0);
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
            panel.add(new JLabel((String) pair[0]), c);
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
        JTable table = new JTable(scriptsModel);
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
        JTable table = new JTable(depsModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton addBtn = new JButton("Add…");
        addBtn.setToolTipText("npm install <package> (Alt: choose dev scope in the prompt)");
        addBtn.addActionListener(e -> {
            JTextField pkgField = new JTextField(20);
            JComboBox<String> scope = new JComboBox<>(new String[]{"dependency", "devDependency"});
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
            runNpm(dev ? List.of("npm", "install", "--save-dev", name)
                    : List.of("npm", "install", name));
        });

        JButton removeBtn = new JButton("Remove");
        removeBtn.setToolTipText("npm uninstall the selected package");
        removeBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                return;
            }
            String name = (String) depsModel.getValueAt(row, 0);
            if (DialogDisplayer.getDefault().notify(new NotifyDescriptor.Confirmation(
                    "npm uninstall " + name + "?", "Remove Dependency",
                    NotifyDescriptor.YES_NO_OPTION)) == NotifyDescriptor.YES_OPTION) {
                runNpm(List.of("npm", "uninstall", name));
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(addBtn);
        buttons.add(removeBtn);
        buttons.add(new JLabel("Changes run npm and refresh when it finishes."));
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    /** Runs an npm mutation and reloads the document when it exits. */
    private void runNpm(List<String> command) {
        CommandExecutor.run("Project Config", projectDir, Map.of(), command,
                line -> {
                }, code -> javax.swing.SwingUtilities.invokeLater(() -> {
                    try {
                        pkg = PackageJsonFile.load(projectDir);
                        loadFields();
                    } catch (IOException ex) {
                        error("Could not run npm: " + ex.getMessage());
                    }
                    if (code != 0) {
                        warn("npm exited with " + code + " — see the \"Rack: Project Config\" output tab.");
                    }
                }));
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

    private boolean saveAll() {
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
                message, NotifyDescriptor.ERROR_MESSAGE));
    }

    private void warn(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                message, NotifyDescriptor.WARNING_MESSAGE));
    }
}

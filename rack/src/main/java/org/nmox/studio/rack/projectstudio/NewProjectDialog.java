package org.nmox.studio.rack.projectstudio;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

/**
 * Start a project: pick a template, name it, and the studio generates
 * working sources plus the pre-wired rack patch, aims the rack at it,
 * and (optionally) kicks off npm install - from empty folder to a
 * running dev loop without leaving the IDE.
 */
public class NewProjectDialog extends JDialog {

    /** Template generation + git init run here, never on the EDT. */
    private static final org.openide.util.RequestProcessor CREATE_RP =
            new org.openide.util.RequestProcessor("nmox-new-project", 1, true);

    private final JTextField nameField = new JTextField("my-app", 20);
    private final JTextField locationField = new JTextField(28);
    private final JList<ProjectTemplates> templateList = new JList<>(ProjectTemplates.values());
    private final JCheckBox installBox = new JCheckBox("Run npm install after creating", true);
    private final JLabel previewLabel = new JLabel(" ");
    private final JButton createButton = new JButton("Create Project");
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton browseButton = new JButton("…");

    private File createdProject;

    public NewProjectDialog(Component parent) {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent), "New Project",
                ModalityType.APPLICATION_MODAL);

        locationField.setText(defaultLocation().getAbsolutePath());

        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.setSelectedIndex(0);
        templateList.setVisibleRowCount(ProjectTemplates.values().length);
        templateList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                ProjectTemplates t = (ProjectTemplates) value;
                setText("<html><b>" + t.getDisplayName() + "</b><br><small>"
                        + t.getDescription() + "</small></html>");
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return this;
            }
        });
        templateList.addListSelectionListener(e -> updatePreview());
        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updatePreview();
            }
        });

        JButton browse = browseButton;
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(new File(locationField.getText()));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Parent Directory for the New Project");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                locationField.setText(chooser.getSelectedFile().getAbsolutePath());
                updatePreview();
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Name:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = 2;
        form.add(nameField, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.gridwidth = 1;
        form.add(new JLabel("Location:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(locationField, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(browse, c);

        c.gridx = 0;
        c.gridy = 2;
        form.add(new JLabel("Template:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        form.add(new JScrollPane(templateList), c);

        c.gridx = 1;
        c.gridy = 3;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(installBox, c);

        c.gridy = 4;
        form.add(previewLabel, c);

        createButton.addActionListener(e -> createProject());
        cancelButton.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelButton);
        buttons.add(createButton);
        getRootPane().setDefaultButton(createButton);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        updatePreview();
        setSize(560, 480);
        setLocationRelativeTo(parent);
    }

    /** The project directory created, or null if cancelled/failed. */
    public File getCreatedProject() {
        return createdProject;
    }

    private static File defaultLocation() {
        File recent = RackService.getDefault().getRecentProjects().stream()
                .findFirst().map(File::getParentFile).orElse(null);
        // the one workspace: fresh launches aim at ~/NMOX (v1.33.1) and Open
        // Folder starts there — the wizard must not invent a second home.
        // Existing users keep their most-recent location.
        return recent != null ? recent : new File(System.getProperty("user.home"), "NMOX");
    }

    private File targetDir() {
        return new File(locationField.getText().trim(), sanitizedName());
    }

    private String sanitizedName() {
        return nameField.getText().trim().toLowerCase()
                .replaceAll("[^a-z0-9-_.]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private void updatePreview() {
        previewLabel.setText("Will create: " + targetDir().getAbsolutePath());
    }

    private void createProject() {
        String name = sanitizedName();
        if (name.isEmpty()) {
            warn("Give the project a name.");
            return;
        }
        ProjectTemplates template = templateList.getSelectedValue();
        File dir = targetDir();
        if (dir.exists()) {
            warn(dir.getName() + " already exists in that location.");
            return;
        }
        // generate + git init are file IO plus up to four git spawns — off the
        // EDT, with the dialog locked (busy) until the outcome is known
        setBusy(true);
        CREATE_RP.post(() -> {
            try {
                template.generate(dir, name);
            } catch (IOException ex) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    setBusy(false);
                    error("Could not create the project: " + ex.getMessage());
                });
                return;
            }
            ProjectTemplates.initGitRepo(dir); // best-effort, each spawn bounded
            javax.swing.SwingUtilities.invokeLater(() -> {
                createdProject = dir;
                // aim the rack: the template's patch mounts automatically
                RackService.getDefault().openProject(dir);
                if (installBox.isSelected()) {
                    // Deliberately NOT trust-gated: this runs code the product
                    // itself just wrote from its own template, at the user's
                    // explicit request — WorkspaceTrust guards OTHER people's
                    // code. Manager resolved via the v1.60.0 detection so a
                    // future manager-pinning template installs with its own tool.
                    String pm = org.nmox.studio.rack.devices.ProjectInspector
                            .nodePackageManager(dir);
                    CommandExecutor.run("Project Setup", dir, Map.of(),
                            List.of(pm, "install"), line -> {
                            }, code -> {
                            });
                }
                dispose();
            });
        });
    }

    /** Locks the form while creation runs; the button says why. */
    private void setBusy(boolean busy) {
        createButton.setEnabled(!busy);
        createButton.setText(busy ? "Creating…" : "Create Project");
        cancelButton.setEnabled(!busy);
        nameField.setEnabled(!busy);
        locationField.setEnabled(!busy);
        templateList.setEnabled(!busy);
        installBox.setEnabled(!busy);
        browseButton.setEnabled(!busy);
    }

    // ---- platform dialogs (parented, keyboard-correct, consistent chrome) ----

    private void warn(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                message, NotifyDescriptor.WARNING_MESSAGE));
    }

    private void error(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                message, NotifyDescriptor.ERROR_MESSAGE));
    }
}

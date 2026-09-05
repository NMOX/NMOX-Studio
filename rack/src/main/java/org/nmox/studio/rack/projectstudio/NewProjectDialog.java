package org.nmox.studio.rack.projectstudio;

import org.nmox.studio.core.spi.LiveRuns;
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
import org.nmox.studio.rack.service.WorkspaceTrust;
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
    // built-ins first, then any ~/.nmox/templates.d drop-ins (v1.293.0) —
    // both kinds render name+description and generate the same way, so the
    // list holds the union and the OK path dispatches on the element type
    private final JList<Object> templateList = new JList<>(templateModel());
    private final JCheckBox installBox = new JCheckBox("Run npm install after creating", true);
    private final JLabel previewLabel = new JLabel(" ");
    private final JButton createButton = new JButton("Create Project");
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton browseButton = new JButton("…");

    private File createdProject;

    /**
     * Built-ins immediately, drop-ins a beat later: the wizard must open
     * without waiting on disk (the v1.33.1 law — even a shallow local scan
     * stays off the EDT, because a network-mounted home must not freeze a
     * menu click). Custom templates join the same model when read; a
     * malformed drop-in is skipped with a status note, never a blocker —
     * the learn-catalog.d law.
     */
    private static javax.swing.ListModel<Object> templateModel() {
        javax.swing.DefaultListModel<Object> model = new javax.swing.DefaultListModel<>();
        for (ProjectTemplates t : ProjectTemplates.values()) {
            model.addElement(t);
        }
        CREATE_RP.post(() -> {
            UserTemplates.Loaded loaded = UserTemplates.load(UserTemplates.dropInDir());
            if (loaded.templates().isEmpty() && loaded.skipped().isEmpty()) {
                return;
            }
            javax.swing.SwingUtilities.invokeLater(() -> {
                loaded.templates().forEach(model::addElement);
                for (UserTemplates.Skipped s : loaded.skipped()) {
                    org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                            "Template " + s.file() + " skipped: " + s.reason());
                }
            });
        });
        return model;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public NewProjectDialog(Component parent) {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent), "New Project",
                ModalityType.APPLICATION_MODAL);

        locationField.setText(defaultLocation().getAbsolutePath());

        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.setSelectedIndex(0);
        templateList.setVisibleRowCount(Math.min(16, templateList.getModel().getSize()));
        templateList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ProjectTemplates t) {
                    setText("<html><b>" + t.getDisplayName() + "</b><br><small>"
                            + t.getDescription() + "</small></html>");
                } else if (value instanceof UserTemplates.Custom c) {
                    // "yours" says where it came from without a second column
                    setText("<html><b>" + escape(c.name()) + "</b> <font color='#888'>·"
                            + " yours</font><br><small>" + escape(c.description())
                            + "</small></html>");
                }
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
        return defaultLocationFrom(RackService.getDefault().getRecentProjects());
    }

    /** Package-private: the pure default-location choice, for tests. */
    static File defaultLocationFrom(java.util.List<File> recentProjects) {
        // internal homes (learning spaces, experiments) are recent PROJECTS
        // but never where a user keeps new work — a fresh gleam tutorial
        // must not make the wizard default to ~/.nmox/learn (found live)
        File internal = new File(System.getProperty("user.home"), ".nmox");
        File recent = recentProjects.stream()
                .map(File::getParentFile)
                .filter(java.util.Objects::nonNull)
                .filter(dir -> !dir.toPath().startsWith(internal.toPath()))
                .findFirst().orElse(null);
        // the one workspace: fresh launches aim at ~/NMOX (v1.33.1) and Open
        // Folder starts there — the wizard must not invent a second home.
        // Existing users keep their most-recent (real) location.
        return recent != null ? recent : new File(System.getProperty("user.home"), "NMOX");
    }

    private File targetDir() {
        return new File(locationField.getText().trim(), sanitizedName());
    }

    private String sanitizedName() {
        return nameField.getText().trim().toLowerCase(java.util.Locale.ROOT)
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
        Object template = templateList.getSelectedValue();
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
                if (template instanceof UserTemplates.Custom custom) {
                    UserTemplates.generate(custom, dir, name);
                } else {
                    ((ProjectTemplates) template).generate(dir, name);
                }
            } catch (IOException ex) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    setBusy(false);
                    error("Could not create the project: " + ex.getMessage());
                });
                return;
            }
            ProjectTemplates.initGitRepo(dir); // best-effort, each spawn bounded
            // The same reasoning the install spawn below already states, applied
            // where it belongs: every byte in this directory was written by our
            // own template seconds ago, so it is not "other people's code" and
            // must not be met with a malware-shaped prompt. Without this the
            // FIRST Run press asks "Do you trust the files in this folder?" —
            // about a folder the IDE itself just created — and its default
            // button is Keep Safe, so a reflexive Enter leaves the beginner
            // with a Run button that honestly does nothing (v1.93.0 serving
            // truth) and no idea why. Experiments and Learning Spaces have
            // pre-trusted their own scaffolds since they shipped; this closes
            // the one generator that didn't. A CUSTOM template is the one
            // exception: its content is drop-in DATA that may have been
            // copied from anywhere, so it is exactly the "other people's
            // code" the trust prompt exists for — no pre-trust, and the
            // optional install below asks first (v1.224.0 spawn-ledger law).
            boolean custom = template instanceof UserTemplates.Custom;
            if (!custom) {
                WorkspaceTrust.trust(dir);
            }
            javax.swing.SwingUtilities.invokeLater(() -> {
                createdProject = dir;
                // aim the rack: the template's patch mounts automatically
                RackService.getDefault().openProject(dir);
                // package.json guard: an install in a project that has no
                // manifest (PHP built-in, a make-based custom) can only fail
                if (installBox.isSelected() && new File(dir, "package.json").isFile()
                        && (!custom || WorkspaceTrust.requestTrust(dir))) {
                    // For built-ins deliberately NOT trust-gated: this runs
                    // code the product itself just wrote from its own
                    // template, at the user's explicit request —
                    // WorkspaceTrust guards OTHER people's code. Manager
                    // resolved via the v1.60.0 detection so a manager-pinning
                    // template installs with its own tool.
                    String pm = org.nmox.studio.rack.devices.ProjectInspector
                            .nodePackageManager(dir);
                    org.openide.awt.StatusDisplayer.getDefault()
                            .setStatusText("Installing dependencies with " + pm + "…");
                    // the install joins the toolbar ■ (v2.71.0): a hung
                    // registry fetch is the beginner's most common wall,
                    // and it had no stop on screen
                    String runLabel = pm + " install — " + dir.getName();
                    String runId = "project-setup:" + dir.getAbsolutePath() + "#" + System.nanoTime();
                    org.netbeans.api.progress.ProgressHandle installing =
                            org.netbeans.api.progress.ProgressHandle.createHandle(runLabel, () -> {
                                LiveRuns.stop(runId);
                                return true;
                            });
                    installing.start();
                    // the spawn leaves the EDT (v2.73.0 review — the v1.57.0
                    // class, here since the install shipped in v2.36.0)
                    org.openide.util.RequestProcessor.getDefault().post(() -> {
                    CommandExecutor.Handle handle = CommandExecutor.run("Project Setup", dir, Map.of(),
                            List.of(pm, "install"), line -> {
                            }, code -> {
                                installing.finish();
                                LiveRuns.remove(runId);
                                if (LiveRuns.wasStoppedByUser(runId)) {
                                    // STOP reads STOPPED (v2.69.15), one registry over:
                                    // the user ended it, so no "didn't finish" dialog
                                    org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                                            "Install stopped — run " + pm + " install when you are ready");
                                    return;
                                }
                                reportInstall(pm, code);
                            });
                    LiveRuns.add(new LiveRuns.Run(runId, runLabel, handle::kill));
                    });
                }
                dispose();
            });
        });
    }

    /**
     * Says how the dependency install went. It used to say nothing at all —
     * both callbacks were empty lambdas — so a failed install (no Node on
     * PATH being the common one for a beginner) was invisible: the output
     * went to a tab the product deliberately never fronts, the exit code was
     * discarded, and the next thing that happened was a SECOND, equally
     * unexplained failure when they pressed Run. Success is a quiet status
     * line; failure is a dialog that names the two places to look.
     */
    private static void reportInstall(String pm, int code) {
        if (code == 0) {
            org.openide.awt.StatusDisplayer.getDefault()
                    .setStatusText("Dependencies installed — press Run to start your project");
            return;
        }
        org.openide.awt.StatusDisplayer.getDefault()
                .setStatusText(pm + " install failed (exit " + code + ")");
        org.openide.NotifyDescriptor d = new org.openide.NotifyDescriptor.Message(
                "<html><b>" + pm + " install didn't finish.</b><br><br>"
                + "Your project files were created fine — only the dependency<br>"
                + "download failed (exit code " + code + ").<br><br>"
                + "Two places to look:<br>"
                + "&nbsp;&nbsp;• <b>Output ▸ Project Setup</b> — the full log of what "
                + pm + " said<br>"
                + "&nbsp;&nbsp;• <b>Tools ▸ Environment Doctor</b> — shows whether Node "
                + "and " + pm + " are installed<br><br>"
                + "You can also just run <code>" + pm + " install</code> again later.</html>",
                org.openide.NotifyDescriptor.WARNING_MESSAGE);
        org.openide.DialogDisplayer.getDefault().notifyLater(d);
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

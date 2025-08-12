package org.nmox.studio.tools.npm;

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;

public class WebProjectWizardPanelVisual extends JPanel implements DocumentListener {

    public static final String PROP_PROJECT_NAME = "projectName";

    private WebProjectWizardPanel panel;

    public WebProjectWizardPanelVisual(WebProjectWizardPanel panel) {
        initComponents();
        this.panel = panel;
        projectNameTextField.getDocument().addDocumentListener(this);
        projectLocationTextField.getDocument().addDocumentListener(this);
    }

    public String getProjectName() {
        return this.projectNameTextField.getText();
    }

    private void initComponents() {
        projectNameLabel = new javax.swing.JLabel();
        projectNameTextField = new javax.swing.JTextField();
        projectLocationLabel = new javax.swing.JLabel();
        projectLocationTextField = new javax.swing.JTextField();
        browseButton = new javax.swing.JButton();
        createdFolderLabel = new javax.swing.JLabel();
        createdFolderTextField = new javax.swing.JTextField();
        projectTypeLabel = new javax.swing.JLabel();
        projectTypeComboBox = new javax.swing.JComboBox<>();

        projectNameLabel.setLabelFor(projectNameTextField);
        org.openide.awt.Mnemonics.setLocalizedText(projectNameLabel, "Project &Name:");

        projectLocationLabel.setLabelFor(projectLocationTextField);
        org.openide.awt.Mnemonics.setLocalizedText(projectLocationLabel, "Project &Location:");

        org.openide.awt.Mnemonics.setLocalizedText(browseButton, "Br&owse...");
        browseButton.setActionCommand("BROWSE");
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        createdFolderLabel.setLabelFor(createdFolderTextField);
        org.openide.awt.Mnemonics.setLocalizedText(createdFolderLabel, "Project &Folder:");

        createdFolderTextField.setEditable(false);

        projectTypeLabel.setLabelFor(projectTypeComboBox);
        org.openide.awt.Mnemonics.setLocalizedText(projectTypeLabel, "Project &Type:");

        projectTypeComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Vanilla JavaScript", "React", "Vue" }));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(projectNameLabel)
                    .addComponent(projectLocationLabel)
                    .addComponent(createdFolderLabel)
                    .addComponent(projectTypeLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(projectNameTextField)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(projectLocationTextField)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(browseButton))
                    .addComponent(createdFolderTextField)
                    .addComponent(projectTypeComboBox, 0, 280, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(projectNameLabel)
                    .addComponent(projectNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(projectLocationLabel)
                    .addComponent(projectLocationTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(browseButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(createdFolderLabel)
                    .addComponent(createdFolderTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(projectTypeLabel)
                    .addComponent(projectTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(191, Short.MAX_VALUE))
        );
    }

    private javax.swing.JButton browseButton;
    private javax.swing.JLabel createdFolderLabel;
    private javax.swing.JTextField createdFolderTextField;
    private javax.swing.JLabel projectLocationLabel;
    private javax.swing.JTextField projectLocationTextField;
    private javax.swing.JLabel projectNameLabel;
    private javax.swing.JTextField projectNameTextField;
    private javax.swing.JLabel projectTypeLabel;
    private javax.swing.JComboBox<String> projectTypeComboBox;

    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {
        String command = evt.getActionCommand();
        if ("BROWSE".equals(command)) {
            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(null);
            chooser.setDialogTitle("Select Project Location");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            String path = this.projectLocationTextField.getText();
            if (path.length() > 0) {
                File f = new File(path);
                if (f.exists()) {
                    chooser.setSelectedFile(f);
                }
            }
            if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(this)) {
                File projectDir = chooser.getSelectedFile();
                projectLocationTextField.setText(FileUtil.normalizeFile(projectDir).getAbsolutePath());
            }
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        updateTexts(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        updateTexts(e);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        updateTexts(e);
    }

    private void updateTexts(DocumentEvent e) {
        Document doc = e.getDocument();
        if (doc == projectNameTextField.getDocument() || doc == projectLocationTextField.getDocument()) {
            String projectName = projectNameTextField.getText();
            String projectLocation = projectLocationTextField.getText();
            createdFolderTextField.setText(projectLocation + File.separatorChar + projectName);
        }
        panel.fireChangeEvent();
    }

    public void read(WizardDescriptor settings) {
        File projectLocation = (File) settings.getProperty("projdir");
        if (projectLocation == null || projectLocation.getParentFile() == null || !projectLocation.getParentFile().isDirectory()) {
            projectLocation = ProjectChooser.getProjectsFolder();
        } else {
            projectLocation = projectLocation.getParentFile();
        }
        this.projectLocationTextField.setText(projectLocation.getAbsolutePath());
        String projectName = (String) settings.getProperty("name");
        if (projectName == null) {
            projectName = "WebApp";
        }
        this.projectNameTextField.setText(projectName);
        this.projectNameTextField.selectAll();
    }

    void store(WizardDescriptor d) {
        String name = projectNameTextField.getText().trim();
        String location = projectLocationTextField.getText().trim();
        String folder = createdFolderTextField.getText().trim();
        
        d.putProperty("projdir", new File(folder));
        d.putProperty("name", name);
        
        String projectType = "vanilla";
        switch (projectTypeComboBox.getSelectedIndex()) {
            case 1:
                projectType = "react";
                break;
            case 2:
                projectType = "vue";
                break;
        }
        d.putProperty("projectType", projectType);
    }

    boolean valid(WizardDescriptor wizardDescriptor) {
        if (projectNameTextField.getText().length() == 0) {
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE,
                    "Project Name is not a valid folder name.");
            return false;
        }
        File f = FileUtil.normalizeFile(new File(projectLocationTextField.getText()).getAbsoluteFile());
        if (!f.isDirectory()) {
            String message = "Project Folder is not a valid path.";
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, message);
            return false;
        }
        final File destFolder = FileUtil.normalizeFile(new File(createdFolderTextField.getText()).getAbsoluteFile());

        File projLoc = destFolder;
        while (projLoc != null && !projLoc.exists()) {
            projLoc = projLoc.getParentFile();
        }
        if (projLoc == null || !projLoc.canWrite()) {
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE,
                    "Project Folder cannot be created.");
            return false;
        }

        if (FileUtil.toFileObject(projLoc) == null) {
            String message = "Project Folder is not a valid path.";
            wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, message);
            return false;
        }

        File[] kids = destFolder.listFiles();
        if (destFolder.exists() && kids != null && kids.length > 0) {
            wizardDescriptor.putProperty(WizardDescriptor.PROP_WARNING_MESSAGE,
                    "Project Folder already exists and is not empty.");
        } else {
            wizardDescriptor.putProperty(WizardDescriptor.PROP_WARNING_MESSAGE, null);
        }
        wizardDescriptor.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, null);
        return true;
    }

    void validate(WizardDescriptor d) throws WizardValidationException {
        // nothing to validate
    }
}
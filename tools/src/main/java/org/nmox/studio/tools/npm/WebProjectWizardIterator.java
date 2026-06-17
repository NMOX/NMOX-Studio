package org.nmox.studio.tools.npm;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.templates.TemplateRegistration;
import org.netbeans.api.templates.TemplateRegistrations;
import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

@TemplateRegistrations({
    @TemplateRegistration(
            folder = "Project/Web",
            displayName = "#WebProject_react_displayName",
            description = "ReactProjectDescription.html",
            iconBase = "org/nmox/studio/tools/npm/web-project.png",
            position = 100
    ),
    @TemplateRegistration(
            folder = "Project/Web",
            displayName = "#WebProject_vue_displayName",
            description = "VueProjectDescription.html",
            iconBase = "org/nmox/studio/tools/npm/web-project.png",
            position = 200
    ),
    @TemplateRegistration(
            folder = "Project/Web",
            displayName = "#WebProject_vanilla_displayName",
            description = "VanillaProjectDescription.html",
            iconBase = "org/nmox/studio/tools/npm/web-project.png",
            position = 300
    )
})
@Messages({
    "WebProject_react_displayName=React Application",
    "WebProject_vue_displayName=Vue Application",
    "WebProject_vanilla_displayName=Vanilla JavaScript Application"
})
public class WebProjectWizardIterator implements WizardDescriptor.InstantiatingIterator<WizardDescriptor> {

    private int index;
    private WizardDescriptor wizard;
    private WizardDescriptor.Panel<WizardDescriptor>[] panels;

    private WizardDescriptor.Panel<WizardDescriptor>[] getPanels() {
        if (panels == null) {
            panels = new WizardDescriptor.Panel[]{
                new WebProjectWizardPanel()
            };
            String[] steps = createSteps();
            for (int i = 0; i < panels.length; i++) {
                Component c = panels[i].getComponent();
                if (steps[i] == null) {
                    steps[i] = c.getName();
                }
                if (c instanceof JComponent) {
                    JComponent jc = (JComponent) c;
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_SELECTED_INDEX, i);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DATA, steps);
                    jc.putClientProperty(WizardDescriptor.PROP_AUTO_WIZARD_STYLE, true);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_DISPLAYED, true);
                    jc.putClientProperty(WizardDescriptor.PROP_CONTENT_NUMBERED, true);
                }
            }
        }
        return panels;
    }

    private String[] createSteps() {
        return new String[]{
            NbBundle.getMessage(WebProjectWizardIterator.class, "LBL_CreateProjectStep")
        };
    }

    @Override
    public Set<?> instantiate() throws IOException {
        Set<FileObject> resultSet = new LinkedHashSet<>();
        File dirF = FileUtil.normalizeFile((File) wizard.getProperty("projdir"));
        dirF.mkdirs();

        FileObject dir = FileUtil.toFileObject(dirF);
        String projectType = (String) wizard.getProperty("projectType");
        
        if (projectType == null) {
            projectType = "vanilla";
        }

        switch (projectType) {
            case "react":
                createReactProject(dir);
                break;
            case "vue":
                createVueProject(dir);
                break;
            default:
                createVanillaProject(dir);
                break;
        }

        resultSet.add(dir);
        
        File parent = dirF.getParentFile();
        if (parent != null && parent.exists()) {
            ProjectChooser.setProjectsFolder(parent);
        }

        return resultSet;
    }

    private void createFileFromTemplate(FileObject targetDir, String fileName, String templatePath, String projectName) throws IOException {
        try (java.io.InputStream is = WebProjectWizardIterator.class.getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new IOException("Template resource not found: " + templatePath);
            }
            byte[] bytes = is.readAllBytes();
            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            String interpolated = content.replace("${projectName}", projectName);
            
            FileObject targetFile = targetDir.createData(fileName);
            Files.write(FileUtil.toFile(targetFile).toPath(), interpolated.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private void createReactProject(FileObject projectDir) throws IOException {
        String projectName = projectDir.getName();
        createFileFromTemplate(projectDir, "package.json", "templates/react/package.json", projectName);
        
        FileObject src = projectDir.createFolder("src");
        createFileFromTemplate(src, "index.js", "templates/react/src/index.js", projectName);
        createFileFromTemplate(src, "App.js", "templates/react/src/App.js", projectName);
        createFileFromTemplate(src, "index.css", "templates/react/src/index.css", projectName);
        
        FileObject publicFolder = projectDir.createFolder("public");
        createFileFromTemplate(publicFolder, "index.html", "templates/react/public/index.html", projectName);
    }

    private void createVueProject(FileObject projectDir) throws IOException {
        String projectName = projectDir.getName();
        createFileFromTemplate(projectDir, "package.json", "templates/vue/package.json", projectName);
        
        FileObject src = projectDir.createFolder("src");
        createFileFromTemplate(src, "main.js", "templates/vue/src/main.js", projectName);
        createFileFromTemplate(src, "App.vue", "templates/vue/src/App.vue", projectName);
        
        createFileFromTemplate(projectDir, "index.html", "templates/vue/index.html", projectName);
        createFileFromTemplate(projectDir, "vite.config.js", "templates/vue/vite.config.js", projectName);
    }

    private void createVanillaProject(FileObject projectDir) throws IOException {
        String projectName = projectDir.getName();
        createFileFromTemplate(projectDir, "package.json", "templates/vanilla/package.json", projectName);
        createFileFromTemplate(projectDir, "index.html", "templates/vanilla/index.html", projectName);
        createFileFromTemplate(projectDir, "script.js", "templates/vanilla/script.js", projectName);
        createFileFromTemplate(projectDir, "style.css", "templates/vanilla/style.css", projectName);
    }

    @Override
    public void initialize(WizardDescriptor wizard) {
        this.wizard = wizard;
    }

    @Override
    public void uninitialize(WizardDescriptor wizard) {
        panels = null;
    }

    @Override
    public WizardDescriptor.Panel<WizardDescriptor> current() {
        return getPanels()[index];
    }

    @Override
    public String name() {
        return index + 1 + ". from " + getPanels().length;
    }

    @Override
    public boolean hasNext() {
        return index < getPanels().length - 1;
    }

    @Override
    public boolean hasPrevious() {
        return index > 0;
    }

    @Override
    public void nextPanel() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        index++;
    }

    @Override
    public void previousPanel() {
        if (!hasPrevious()) {
            throw new NoSuchElementException();
        }
        index--;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }
}
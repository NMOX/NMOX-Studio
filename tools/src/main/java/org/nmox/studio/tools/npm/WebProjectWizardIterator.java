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

    private void createReactProject(FileObject projectDir) throws IOException {
        String packageJson = "{\n" +
                "  \"name\": \"" + projectDir.getName() + "\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"private\": true,\n" +
                "  \"dependencies\": {\n" +
                "    \"react\": \"^18.2.0\",\n" +
                "    \"react-dom\": \"^18.2.0\"\n" +
                "  },\n" +
                "  \"scripts\": {\n" +
                "    \"start\": \"react-scripts start\",\n" +
                "    \"build\": \"react-scripts build\",\n" +
                "    \"test\": \"react-scripts test\",\n" +
                "    \"eject\": \"react-scripts eject\"\n" +
                "  },\n" +
                "  \"devDependencies\": {\n" +
                "    \"react-scripts\": \"5.0.1\"\n" +
                "  }\n" +
                "}";
        
        FileObject packageJsonFile = projectDir.createData("package.json");
        Files.write(FileUtil.toFile(packageJsonFile).toPath(), packageJson.getBytes());
        
        FileObject src = projectDir.createFolder("src");
        FileObject indexJs = src.createData("index.js");
        String indexContent = "import React from 'react';\n" +
                "import ReactDOM from 'react-dom/client';\n" +
                "import './index.css';\n" +
                "import App from './App';\n\n" +
                "const root = ReactDOM.createRoot(document.getElementById('root'));\n" +
                "root.render(\n" +
                "  <React.StrictMode>\n" +
                "    <App />\n" +
                "  </React.StrictMode>\n" +
                ");";
        Files.write(FileUtil.toFile(indexJs).toPath(), indexContent.getBytes());
        
        FileObject appJs = src.createData("App.js");
        String appContent = "import React from 'react';\n\n" +
                "function App() {\n" +
                "  return (\n" +
                "    <div className=\"App\">\n" +
                "      <h1>Welcome to React</h1>\n" +
                "      <p>Edit src/App.js and save to reload.</p>\n" +
                "    </div>\n" +
                "  );\n" +
                "}\n\n" +
                "export default App;";
        Files.write(FileUtil.toFile(appJs).toPath(), appContent.getBytes());
        
        FileObject indexCss = src.createData("index.css");
        String cssContent = "body {\n" +
                "  margin: 0;\n" +
                "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',\n" +
                "    'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue',\n" +
                "    sans-serif;\n" +
                "  -webkit-font-smoothing: antialiased;\n" +
                "  -moz-osx-font-smoothing: grayscale;\n" +
                "}";
        Files.write(FileUtil.toFile(indexCss).toPath(), cssContent.getBytes());
        
        FileObject publicFolder = projectDir.createFolder("public");
        FileObject indexHtml = publicFolder.createData("index.html");
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\" />\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\n" +
                "  <title>React App</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <noscript>You need to enable JavaScript to run this app.</noscript>\n" +
                "  <div id=\"root\"></div>\n" +
                "</body>\n" +
                "</html>";
        Files.write(FileUtil.toFile(indexHtml).toPath(), htmlContent.getBytes());
    }

    private void createVueProject(FileObject projectDir) throws IOException {
        String packageJson = "{\n" +
                "  \"name\": \"" + projectDir.getName() + "\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"private\": true,\n" +
                "  \"scripts\": {\n" +
                "    \"dev\": \"vite\",\n" +
                "    \"build\": \"vite build\",\n" +
                "    \"preview\": \"vite preview\"\n" +
                "  },\n" +
                "  \"dependencies\": {\n" +
                "    \"vue\": \"^3.3.4\"\n" +
                "  },\n" +
                "  \"devDependencies\": {\n" +
                "    \"@vitejs/plugin-vue\": \"^4.2.3\",\n" +
                "    \"vite\": \"^4.4.5\"\n" +
                "  }\n" +
                "}";
        
        FileObject packageJsonFile = projectDir.createData("package.json");
        Files.write(FileUtil.toFile(packageJsonFile).toPath(), packageJson.getBytes());
        
        FileObject src = projectDir.createFolder("src");
        FileObject mainJs = src.createData("main.js");
        String mainContent = "import { createApp } from 'vue'\n" +
                "import App from './App.vue'\n\n" +
                "createApp(App).mount('#app')";
        Files.write(FileUtil.toFile(mainJs).toPath(), mainContent.getBytes());
        
        FileObject appVue = src.createData("App.vue");
        String appContent = "<template>\n" +
                "  <div id=\"app\">\n" +
                "    <h1>{{ msg }}</h1>\n" +
                "    <p>Edit src/App.vue and save to reload.</p>\n" +
                "  </div>\n" +
                "</template>\n\n" +
                "<script>\n" +
                "export default {\n" +
                "  name: 'App',\n" +
                "  data() {\n" +
                "    return {\n" +
                "      msg: 'Welcome to Vue'\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "</script>\n\n" +
                "<style>\n" +
                "#app {\n" +
                "  font-family: Avenir, Helvetica, Arial, sans-serif;\n" +
                "  -webkit-font-smoothing: antialiased;\n" +
                "  -moz-osx-font-smoothing: grayscale;\n" +
                "  text-align: center;\n" +
                "  color: #2c3e50;\n" +
                "  margin-top: 60px;\n" +
                "}\n" +
                "</style>";
        Files.write(FileUtil.toFile(appVue).toPath(), appContent.getBytes());
        
        FileObject indexHtml = projectDir.createData("index.html");
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Vue App</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div id=\"app\"></div>\n" +
                "  <script type=\"module\" src=\"/src/main.js\"></script>\n" +
                "</body>\n" +
                "</html>";
        Files.write(FileUtil.toFile(indexHtml).toPath(), htmlContent.getBytes());
        
        FileObject viteConfig = projectDir.createData("vite.config.js");
        String viteContent = "import { defineConfig } from 'vite'\n" +
                "import vue from '@vitejs/plugin-vue'\n\n" +
                "export default defineConfig({\n" +
                "  plugins: [vue()]\n" +
                "})";
        Files.write(FileUtil.toFile(viteConfig).toPath(), viteContent.getBytes());
    }

    private void createVanillaProject(FileObject projectDir) throws IOException {
        String packageJson = "{\n" +
                "  \"name\": \"" + projectDir.getName() + "\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"description\": \"Vanilla JavaScript application\",\n" +
                "  \"main\": \"index.js\",\n" +
                "  \"scripts\": {\n" +
                "    \"start\": \"npx http-server -o\",\n" +
                "    \"dev\": \"npx http-server\"\n" +
                "  },\n" +
                "  \"devDependencies\": {\n" +
                "    \"http-server\": \"^14.1.1\"\n" +
                "  }\n" +
                "}";
        
        FileObject packageJsonFile = projectDir.createData("package.json");
        Files.write(FileUtil.toFile(packageJsonFile).toPath(), packageJson.getBytes());
        
        FileObject indexHtml = projectDir.createData("index.html");
        String htmlContent = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Vanilla JS App</title>\n" +
                "  <link rel=\"stylesheet\" href=\"style.css\">\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div id=\"app\">\n" +
                "    <h1>Welcome to Vanilla JavaScript</h1>\n" +
                "    <p>Edit index.html, style.css, and script.js to get started.</p>\n" +
                "  </div>\n" +
                "  <script src=\"script.js\"></script>\n" +
                "</body>\n" +
                "</html>";
        Files.write(FileUtil.toFile(indexHtml).toPath(), htmlContent.getBytes());
        
        FileObject scriptJs = projectDir.createData("script.js");
        String jsContent = "// Vanilla JavaScript Application\n" +
                "document.addEventListener('DOMContentLoaded', function() {\n" +
                "  console.log('App loaded!');\n" +
                "  \n" +
                "  // Add your JavaScript code here\n" +
                "  const app = document.getElementById('app');\n" +
                "  \n" +
                "  // Example: Add a button\n" +
                "  const button = document.createElement('button');\n" +
                "  button.textContent = 'Click me!';\n" +
                "  button.addEventListener('click', function() {\n" +
                "    alert('Hello from Vanilla JS!');\n" +
                "  });\n" +
                "  \n" +
                "  app.appendChild(button);\n" +
                "});";
        Files.write(FileUtil.toFile(scriptJs).toPath(), jsContent.getBytes());
        
        FileObject styleCss = projectDir.createData("style.css");
        String cssContent = "body {\n" +
                "  margin: 0;\n" +
                "  padding: 0;\n" +
                "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',\n" +
                "    'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue',\n" +
                "    sans-serif;\n" +
                "  -webkit-font-smoothing: antialiased;\n" +
                "  -moz-osx-font-smoothing: grayscale;\n" +
                "}\n\n" +
                "#app {\n" +
                "  text-align: center;\n" +
                "  padding: 2rem;\n" +
                "}\n\n" +
                "button {\n" +
                "  background-color: #4CAF50;\n" +
                "  border: none;\n" +
                "  color: white;\n" +
                "  padding: 15px 32px;\n" +
                "  text-align: center;\n" +
                "  text-decoration: none;\n" +
                "  display: inline-block;\n" +
                "  font-size: 16px;\n" +
                "  margin: 4px 2px;\n" +
                "  cursor: pointer;\n" +
                "  border-radius: 4px;\n" +
                "}";
        Files.write(FileUtil.toFile(styleCss).toPath(), cssContent.getBytes());
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
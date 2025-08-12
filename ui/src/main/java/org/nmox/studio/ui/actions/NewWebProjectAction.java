package org.nmox.studio.ui.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "File",
        id = "org.nmox.studio.ui.actions.NewWebProjectAction"
)
@ActionRegistration(
        displayName = "#CTL_NewWebProjectAction"
)
@ActionReference(path = "Menu/File", position = 100)
@Messages("CTL_NewWebProjectAction=New Web Project...")
public final class NewWebProjectAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Project Location");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File selectedDir = chooser.getSelectedFile();
            String projectName = JOptionPane.showInputDialog(null, 
                    "Enter project name:", 
                    "New Web Project", 
                    JOptionPane.QUESTION_MESSAGE);
            
            if (projectName != null && !projectName.trim().isEmpty()) {
                createWebProject(new File(selectedDir, projectName));
            }
        }
    }

    private void createWebProject(File projectDir) {
        try {
            if (!projectDir.exists()) {
                projectDir.mkdirs();
            }

            createFile(projectDir, "index.html", 
                    "<!DOCTYPE html>\n" +
                    "<html lang=\"en\">\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "    <title>" + projectDir.getName() + "</title>\n" +
                    "    <link rel=\"stylesheet\" href=\"styles.css\">\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <h1>Welcome to " + projectDir.getName() + "</h1>\n" +
                    "    <script src=\"script.js\"></script>\n" +
                    "</body>\n" +
                    "</html>");

            createFile(projectDir, "styles.css",
                    "* {\n" +
                    "    margin: 0;\n" +
                    "    padding: 0;\n" +
                    "    box-sizing: border-box;\n" +
                    "}\n\n" +
                    "body {\n" +
                    "    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;\n" +
                    "    line-height: 1.6;\n" +
                    "    color: #333;\n" +
                    "    padding: 20px;\n" +
                    "}\n\n" +
                    "h1 {\n" +
                    "    color: #007acc;\n" +
                    "}");

            createFile(projectDir, "script.js",
                    "document.addEventListener('DOMContentLoaded', function() {\n" +
                    "    console.log('Project " + projectDir.getName() + " loaded!');\n" +
                    "});\n");

            createFile(projectDir, "package.json",
                    "{\n" +
                    "  \"name\": \"" + projectDir.getName().toLowerCase() + "\",\n" +
                    "  \"version\": \"1.0.0\",\n" +
                    "  \"description\": \"A web project created with NMOX Studio\",\n" +
                    "  \"main\": \"script.js\",\n" +
                    "  \"scripts\": {\n" +
                    "    \"start\": \"echo 'Use NMOX Studio Live Preview'\",\n" +
                    "    \"build\": \"echo 'Add your build script here'\",\n" +
                    "    \"test\": \"echo 'Add your test script here'\"\n" +
                    "  },\n" +
                    "  \"keywords\": [],\n" +
                    "  \"author\": \"\",\n" +
                    "  \"license\": \"MIT\"\n" +
                    "}");

            createFile(projectDir, "nmox-web.json",
                    "{\n" +
                    "  \"projectType\": \"web\",\n" +
                    "  \"framework\": \"vanilla\",\n" +
                    "  \"created\": \"" + System.currentTimeMillis() + "\"\n" +
                    "}");

            JOptionPane.showMessageDialog(null, 
                    "Web project created successfully at:\n" + projectDir.getAbsolutePath(),
                    "Project Created", 
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            JOptionPane.showMessageDialog(null, 
                    "Failed to create project: " + ex.getMessage(),
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createFile(File dir, String filename, String content) throws IOException {
        File file = new File(dir, filename);
        java.nio.file.Files.write(file.toPath(), content.getBytes());
    }
}
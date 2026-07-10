package org.nmox.studio.ui.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JFileChooser;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

@ActionID(
        category = "File",
        id = "org.nmox.studio.ui.actions.OpenFolderAction"
)
@ActionRegistration(
        displayName = "#CTL_OpenFolderAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 60),
    // Cmd+Alt+O: ⇧⌘O belongs to the platform's Open Project (projectui),
    // which won every time and dropped the user into ~/NetBeansProjects —
    // a folder NMOX never creates. See WindowShortcutsTest.
    @ActionReference(path = "Shortcuts", name = "DA-O")
})
@Messages("CTL_OpenFolderAction=Open Folder...")
public final class OpenFolderAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);

        chooser.setCurrentDirectory(startDirectory());

        int result = chooser.showOpenDialog(null);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            if (selectedFolder != null && selectedFolder.exists() && selectedFolder.isDirectory()) {
                openFolder(selectedFolder);
            }
        }
    }

    /**
     * Start where the user's projects actually live. $HOME is a wall of
     * Library/Desktop/Downloads noise (and on macOS the TCC-protected ones
     * prompt); the workspace is where New Project puts things.
     */
    private static File startDirectory() {
        File workspace = new File(System.getProperty("user.home"), "NMOX");
        return workspace.isDirectory()
                ? workspace : new File(System.getProperty("user.home"));
    }

    private void openFolder(File folder) {
        try {
            // aim the whole IDE - rack, studio, workbench - at the folder
            org.nmox.studio.rack.service.RackService.getDefault().openProject(folder);
        } catch (RuntimeException | LinkageError ex) {
            // rack unavailable; still surface the workbench below
        }
        TopComponent workbench = WindowManager.getDefault()
                .findTopComponent("ProjectExplorerTopComponent");
        if (workbench != null) {
            workbench.open();
            workbench.requestActive();
        }
    }
}

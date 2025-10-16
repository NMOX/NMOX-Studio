package org.nmox.studio.ui.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JFileChooser;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileUtil;
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
    @ActionReference(path = "Shortcuts", name = "DS-O")
})
@Messages("CTL_OpenFolderAction=Open Folder...")
public final class OpenFolderAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);

        // Set to user's home directory by default
        chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        int result = chooser.showOpenDialog(null);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            if (selectedFolder != null && selectedFolder.exists() && selectedFolder.isDirectory()) {
                openFolder(selectedFolder);
            }
        }
    }

    private void openFolder(File folder) {
        try {
            org.openide.filesystems.FileObject folderObject = FileUtil.toFileObject(folder);
            if (folderObject != null) {
                // Find Project Explorer and activate it
                TopComponent projectExplorer = WindowManager.getDefault().findTopComponent("ProjectExplorerTopComponent");
                if (projectExplorer != null) {
                    projectExplorer.open();
                    projectExplorer.requestActive();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

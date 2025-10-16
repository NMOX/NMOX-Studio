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
import org.openide.loaders.DataObject;
import org.openide.cookies.OpenCookie;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "File",
        id = "org.nmox.studio.ui.actions.OpenFileAction"
)
@ActionRegistration(
        displayName = "#CTL_OpenFileAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 50),
    @ActionReference(path = "Shortcuts", name = "D-O")
})
@Messages("CTL_OpenFileAction=Open File...")
public final class OpenFileAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open File");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(true);

        // Set to user's home directory by default
        chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        int result = chooser.showOpenDialog(null);

        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = chooser.getSelectedFiles();
            if (selectedFiles.length == 0) {
                selectedFiles = new File[]{chooser.getSelectedFile()};
            }

            for (File file : selectedFiles) {
                if (file != null && file.exists() && file.isFile()) {
                    openFile(file);
                }
            }
        }
    }

    private void openFile(File file) {
        try {
            org.openide.filesystems.FileObject fileObject = FileUtil.toFileObject(file);
            if (fileObject != null) {
                DataObject dataObject = DataObject.find(fileObject);
                OpenCookie openCookie = dataObject.getLookup().lookup(OpenCookie.class);
                if (openCookie != null) {
                    openCookie.open();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

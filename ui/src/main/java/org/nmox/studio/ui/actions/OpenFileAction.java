package org.nmox.studio.ui.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.cookies.OpenCookie;
import org.openide.util.NbBundle.Messages;

/**
 * File ▸ Open File… (⌘O): a plain multi-select file chooser whose picks
 * open in the editor through the platform's own machinery — File →
 * {@code FileObject} → {@link DataObject} → {@link OpenCookie} — so the
 * right editor kit answers for each MIME type and files with no
 * registered editor get an honest warning dialog instead of a silent
 * no-op. Registered via {@code @ActionID} under Menu/File with the
 * standard shortcut; stateless, EDT-invoked like every menu action.
 */
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
            for (File file : chooser.getSelectedFiles()) {
                if (file != null && file.exists() && file.isFile() && !openFile(file)) {
                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                            "Could not open " + file.getName()
                            + " — no editor is registered for this file.",
                            NotifyDescriptor.WARNING_MESSAGE));
                }
            }
        }
    }

    private boolean openFile(File file) {
        try {
            org.openide.filesystems.FileObject fileObject = FileUtil.toFileObject(file);
            if (fileObject == null) {
                return false;
            }
            DataObject dataObject = DataObject.find(fileObject);
            OpenCookie openCookie = dataObject.getLookup().lookup(OpenCookie.class);
            if (openCookie == null) {
                return false;
            }
            openCookie.open();
            return true;
        } catch (Exception ex) {
            Logger.getLogger(OpenFileAction.class.getName())
                    .log(Level.WARNING, "Failed to open " + file, ex);
            return false;
        }
    }
}

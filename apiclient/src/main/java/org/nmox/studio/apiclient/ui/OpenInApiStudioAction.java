package org.nmox.studio.apiclient.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;

/**
 * The VS Code REST-Client gesture (v1.195.0): a repo's {@code .http}/
 * {@code .rest} file is already open in the editor — right-click it and
 * it lands in API Studio as a collection, no Import… menu, no file
 * chooser re-finding a file that is on screen. Registered on the
 * editor popup AND the file node's menu, context-injected with the
 * file's DataObject so the item only appears where it can act.
 * The import itself is the exact chooser path: same off-EDT read, same
 * {@code HttpFileCodec}, same secrets-law Authorization lift.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.apiclient.ui.OpenInApiStudioAction")
@ActionRegistration(displayName = "#CTL_OpenInApiStudio", lazy = true)
@ActionReferences({
    @ActionReference(path = "Editors/text/x-http-request/Popup", position = 1950),
    @ActionReference(path = "Loaders/text/x-http-request/Actions", position = 250)
})
@Messages("CTL_OpenInApiStudio=Open in API Studio")
public final class OpenInApiStudioAction implements ActionListener {

    private final DataObject context;

    public OpenInApiStudioAction(DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        File file = FileUtil.toFile(context.getPrimaryFile());
        if (file == null) {
            return; // virtual/archive file — nothing on disk to read
        }
        ApiClientTopComponent.importHttpFileFromEditor(file);
    }
}

package org.nmox.studio.editor.javascript;

import java.io.IOException;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.text.MultiViewEditorElement;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * DataObject for JavaScript files with proper MIME type registration.
 * Handles .js, .mjs, and .jsx files.
 */
@Messages({
    "LBL_JavaScript_LOADER=JavaScript Files"
})
@MIMEResolver.ExtensionRegistration(
    displayName = "#LBL_JavaScript_LOADER",
    mimeType = "text/javascript",
    extension = {"js", "mjs", "jsx"},
    position = 100
)
@DataObject.Registration(
    mimeType = "text/javascript",
    iconBase = "org/nmox/studio/editor/javascript/javascript.png",
    displayName = "#LBL_JavaScript_LOADER",
    position = 100
)
@ActionReferences({
    @ActionReference(
        path = "Loaders/text/javascript/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.OpenAction"),
        position = 100,
        separatorAfter = 200
    ),
    @ActionReference(
        path = "Loaders/text/javascript/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.CutAction"),
        position = 300
    ),
    @ActionReference(
        path = "Loaders/text/javascript/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.CopyAction"),
        position = 400
    ),
    @ActionReference(
        path = "Loaders/text/javascript/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.PasteAction"),
        position = 500,
        separatorAfter = 600
    ),
    @ActionReference(
        path = "Loaders/text/javascript/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.DeleteAction"),
        position = 700
    ),
    @ActionReference(
        path = "Loaders/text/javascript/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.RenameAction"),
        position = 800,
        separatorAfter = 900
    ),
    @ActionReference(
        path = "Loaders/text/javascript/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.SaveAsTemplateAction"),
        position = 1000,
        separatorAfter = 1100
    ),
    @ActionReference(
        path = "Loaders/text/javascript/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.FileSystemAction"),
        position = 1200,
        separatorAfter = 1300
    ),
    @ActionReference(
        path = "Loaders/text/javascript/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.ToolsAction"),
        position = 1400
    ),
    @ActionReference(
        path = "Loaders/text/javascript/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.PropertiesAction"),
        position = 1500
    )
})
public class JavaScriptDataObject extends MultiDataObject {

    public JavaScriptDataObject(FileObject pf, MultiFileLoader loader) 
            throws DataObjectExistsException, IOException {
        super(pf, loader);
        registerEditor("text/javascript", true);
    }

    @Override
    protected int associateLookup() {
        return 1;
    }

    @MultiViewElement.Registration(
        displayName = "#LBL_JavaScript_EDITOR",
        iconBase = "org/nmox/studio/editor/javascript/javascript.png",
        mimeType = "text/javascript",
        persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED,
        preferredID = "JavaScript",
        position = 1000
    )
    @Messages("LBL_JavaScript_EDITOR=Source")
    public static MultiViewEditorElement createEditor(Lookup lkp) {
        return new MultiViewEditorElement(lkp);
    }
}
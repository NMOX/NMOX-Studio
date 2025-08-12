package org.nmox.studio.editor.typescript;

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
 * DataObject for TypeScript files.
 * Handles .ts and .tsx files.
 */
@Messages({
    "LBL_TypeScript_LOADER=TypeScript Files"
})
@MIMEResolver.ExtensionRegistration(
    displayName = "#LBL_TypeScript_LOADER",
    mimeType = "text/typescript",
    extension = {"ts", "tsx"},
    position = 110
)
@DataObject.Registration(
    mimeType = "text/typescript",
    iconBase = "org/nmox/studio/editor/typescript/typescript.png",
    displayName = "#LBL_TypeScript_LOADER",
    position = 110
)
@ActionReferences({
    @ActionReference(
        path = "Loaders/text/typescript/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.OpenAction"),
        position = 100,
        separatorAfter = 200
    ),
    @ActionReference(
        path = "Loaders/text/typescript/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.CutAction"),
        position = 300
    ),
    @ActionReference(
        path = "Loaders/text/typescript/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.CopyAction"),
        position = 400
    ),
    @ActionReference(
        path = "Loaders/text/typescript/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.PasteAction"),
        position = 500,
        separatorAfter = 600
    ),
    @ActionReference(
        path = "Loaders/text/typescript/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.DeleteAction"),
        position = 700
    ),
    @ActionReference(
        path = "Loaders/text/typescript/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.RenameAction"),
        position = 800,
        separatorAfter = 900
    ),
    @ActionReference(
        path = "Loaders/text/typescript/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.PropertiesAction"),
        position = 1500
    )
})
public class TypeScriptDataObject extends MultiDataObject {

    public TypeScriptDataObject(FileObject pf, MultiFileLoader loader) 
            throws DataObjectExistsException, IOException {
        super(pf, loader);
        registerEditor("text/typescript", true);
    }

    @Override
    protected int associateLookup() {
        return 1;
    }

    @MultiViewElement.Registration(
        displayName = "#LBL_TypeScript_EDITOR",
        iconBase = "org/nmox/studio/editor/typescript/typescript.png",
        mimeType = "text/typescript",
        persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED,
        preferredID = "TypeScript",
        position = 1000
    )
    @Messages("LBL_TypeScript_EDITOR=Source")
    public static MultiViewEditorElement createEditor(Lookup lkp) {
        return new MultiViewEditorElement(lkp);
    }
}
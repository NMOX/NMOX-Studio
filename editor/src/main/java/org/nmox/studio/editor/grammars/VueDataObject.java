package org.nmox.studio.editor.grammars;

import java.io.IOException;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.text.MultiViewEditorElement;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * DataObject for Vue single-file components (v2.14.0 — the walk's
 * find, the v1.217.0 law's second half): a mime with a grammar, a
 * kit, actions and an LSP provider but NO LOADER opens through
 * {@code DefaultDataObject}, whose editor never consults the mime's
 * {@code EditorKit} — so the {@link VueEditorKit} existed and the
 * pane still ignored it: chords dead, popup bare, proven by the
 * ⌥⌘E differential (expands in HTML, inert in .vue) in the same
 * session. {@code registerEditor(mime, true)} is what actually binds
 * pane → kit. The MIME resolver itself stays on {@link VueGrammar}.
 */
@Messages({
    "LBL_Vue_LOADER=Vue Components"
})
@DataObject.Registration(
    mimeType = "text/x-vue",
    displayName = "#LBL_Vue_LOADER",
    position = 2420
)
@ActionReferences({
    @ActionReference(
        path = "Loaders/text/x-vue/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.OpenAction"),
        position = 100,
        separatorAfter = 200
    ),
    @ActionReference(
        path = "Loaders/text/x-vue/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.CutAction"),
        position = 300
    ),
    @ActionReference(
        path = "Loaders/text/x-vue/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.CopyAction"),
        position = 400
    ),
    @ActionReference(
        path = "Loaders/text/x-vue/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.PasteAction"),
        position = 500,
        separatorAfter = 600
    ),
    @ActionReference(
        path = "Loaders/text/x-vue/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.DeleteAction"),
        position = 700
    ),
    @ActionReference(
        path = "Loaders/text/x-vue/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.RenameAction"),
        position = 800,
        separatorAfter = 900
    ),
    @ActionReference(
        path = "Loaders/text/x-vue/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.FileSystemAction"),
        position = 1200,
        separatorAfter = 1300
    ),
    @ActionReference(
        path = "Loaders/text/x-vue/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.ToolsAction"),
        position = 1400
    ),
    @ActionReference(
        path = "Loaders/text/x-vue/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.PropertiesAction"),
        position = 1500
    )
})
public class VueDataObject extends MultiDataObject {

    public VueDataObject(FileObject pf, MultiFileLoader loader)
            throws DataObjectExistsException, IOException {
        super(pf, loader);
        registerEditor("text/x-vue", true);
    }

    @Override
    protected int associateLookup() {
        return 1;
    }

    @MultiViewElement.Registration(
        displayName = "#LBL_Vue_EDITOR",
        mimeType = "text/x-vue",
        persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED,
        preferredID = "Vue",
        position = 1000
    )
    @Messages("LBL_Vue_EDITOR=Source")
    public static MultiViewEditorElement createEditor(Lookup lkp) {
        return new MultiViewEditorElement(lkp);
    }
}

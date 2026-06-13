package org.nmox.studio.editor;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.NbBundle.Messages;

@Messages({
    "LBL_Web_LOADER=HTML Files"
})
// only true HTML here: stylesheets, scripts and JSON each have their own
// MIME identity (resolvers in editor.grammars / javascript / typescript)
@MIMEResolver.ExtensionRegistration(
        displayName = "#LBL_Web_LOADER",
        mimeType = "text/html",
        extension = {"html", "htm", "xhtml"},
        position = 979
)
@DataObject.Registration(
        mimeType = "text/html",
        displayName = "#LBL_Web_LOADER",
        position = 979
)
public class WebFileSupport extends MultiDataObject {

    public WebFileSupport(FileObject pf, MultiFileLoader loader) throws DataObjectExistsException {
        super(pf, loader);
        registerEditor("text/html", true);
    }

    @Override
    protected int associateLookup() {
        return 1;
    }
}
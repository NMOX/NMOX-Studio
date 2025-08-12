package org.nmox.studio.editor;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.NbBundle.Messages;

@Messages({
    "LBL_Web_LOADER=Web Files"
})
@MIMEResolver.ExtensionRegistration(
        displayName = "#LBL_Web_LOADER",
        mimeType = "text/html",
        extension = {"html", "htm", "xhtml", "css", "scss", "sass", "less", "js", "jsx", "ts", "tsx", "json"},
        position = 300
)
@DataObject.Registration(
        mimeType = "text/html",
        displayName = "#LBL_Web_LOADER",
        position = 300
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
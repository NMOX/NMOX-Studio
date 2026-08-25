package org.nmox.studio.editor;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.NbBundle.Messages;

/**
 * The DataObject behind every HTML file: it is what makes .html/.htm/.xhtml
 * open in an editor tab at all. The platform matches files to loaders by
 * MIME type, so this class carries both halves of that contract — the
 * {@code @MIMEResolver.ExtensionRegistration} that stamps the extensions
 * as text/html, and the {@code @DataObject.Registration} that tells the
 * loader infrastructure to wrap matching files in this class. The
 * constructor's {@code registerEditor} call is what binds the multiview
 * editor to the MIME type; everything else (icons, cookies, Open action)
 * is inherited MultiDataObject behaviour.
 */
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
        // ledger 82, the suffixless-Angular-template seam: the FILE mime
        // is unwinnable (declarative resolvers cannot see siblings and
        // always precede Lookup-provided ones — decompiled, see the
        // layer's Loaders comment), but the EDITOR mime is this
        // DataObject's to choose. Two structural signals (same-basename
        // .ts sibling carrying @Component, angular.json ancestry) route
        // widget.html to the Angular template editor; every other html
        // file is untouched. Cost on the miss path: one sibling stat.
        java.io.File onDisk = org.openide.filesystems.FileUtil.toFile(pf);
        boolean ngTemplate = onDisk != null
                && org.nmox.studio.editor.angular.NgSuffixless.isSuffixlessTemplate(onDisk);
        registerEditor(ngTemplate ? "text/x-ng-template" : "text/html", true);
    }

    @Override
    protected int associateLookup() {
        return 1;
    }
}
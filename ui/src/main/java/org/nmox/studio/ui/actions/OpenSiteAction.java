package org.nmox.studio.ui.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import org.nmox.studio.ui.site.SiteServer;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle.Messages;

/**
 * Help ▸ NMOX Studio Website (local) — the product's own site, shipped
 * in the cluster and served to you by the app itself on localhost
 * (v2.40.0, David's call). First press starts the {@link SiteServer}
 * on a free loopback port and registers the serving so the ⇄ chip
 * lights on the product's own story; later presses reuse it. The
 * in-app Browser opens at it (system browser as the never-a-dead-click
 * fallback). Honest refusal when the site files are missing from the
 * install.
 */
@ActionID(category = "Help", id = "org.nmox.studio.ui.actions.OpenSiteAction")
@ActionRegistration(displayName = "#CTL_OpenSiteAction")
@ActionReferences({
    @ActionReference(path = "Menu/Help", position = 225)
})
@Messages("CTL_OpenSiteAction=NMOX Studio Website (local)")
public final class OpenSiteAction implements ActionListener {

    private static SiteServer server;

    @Override
    public void actionPerformed(ActionEvent e) {
        File index = InstalledFileLocator.getDefault().locate(
                "website/index.html", "org.nmox.NMOX.Studio.ui", false);
        if (index == null || !index.isFile()) {
            StatusDisplayer.getDefault().setStatusText(
                    "The bundled website is missing from this install — "
                    + "browse https://github.com/NMOX/NMOX-Studio instead.");
            return;
        }
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            try {
                String url = serverFor(index.getParentFile()).start();
                org.nmox.studio.rack.service.ServingRegistry.getDefault().register(
                        new org.nmox.studio.rack.service.ServingRegistry.Serving(
                                "nmox-site", "NMOX SITE", url,
                                org.nmox.studio.rack.service.ServingRegistry.Kind.WEB,
                                index.getParentFile()));
                javax.swing.SwingUtilities.invokeLater(() -> {
                    boolean opened = new org.nmox.studio.ui.browser
                            .EmbeddedBrowserProvider().open(url);
                    StatusDisplayer.getDefault().setStatusText(
                            "The product's own site, served by the product — " + url
                            + (opened ? "" : " (opened in your system browser)"));
                });
            } catch (Exception ex) {
                javax.swing.SwingUtilities.invokeLater(() ->
                        StatusDisplayer.getDefault().setStatusText(
                                "Could not serve the site: " + ex.getMessage()));
            }
        });
    }

    private static synchronized SiteServer serverFor(File root) {
        if (server == null) {
            server = new SiteServer(root);
        }
        return server;
    }
}

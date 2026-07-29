package org.nmox.studio.ui.browser;

import java.awt.EventQueue;
import org.nmox.studio.core.spi.EmbeddedBrowser;
import org.openide.awt.HtmlBrowser;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.cookies.InstanceCookie;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * The {@link EmbeddedBrowser} facade implementation (the OracleAsk
 * idiom): the rack's SCOPE device and the serving chip route here
 * without depending on the ui module. Also owns the one lookup for
 * the platform's embedded WebKit factory.
 *
 * <p>The factory lives in the system filesystem at
 * {@code Services/Browsers/webviewBrowser.settings}
 * ({@code org.netbeans.core.browser.webview.BrowserFactory}), which
 * hides itself when JavaFX is absent — so "is the settings file
 * visible" IS the engine-availability probe, no classloading tricks.
 */
@ServiceProvider(service = EmbeddedBrowser.class)
public final class EmbeddedBrowserProvider implements EmbeddedBrowser {

    @Override
    public boolean open(String url) {
        if (embeddedFactory() == null) {
            return false;
        }
        Runnable show = () -> {
            TopComponent tc = WindowManager.getDefault()
                    .findTopComponent("WebBrowserTopComponent");
            if (tc instanceof WebBrowserTopComponent web) {
                web.showUrl(url);
            }
        };
        if (EventQueue.isDispatchThread()) {
            show.run();
        } else {
            EventQueue.invokeLater(show);
        }
        return true;
    }

    /** The embedded WebKit factory, or null when JavaFX is absent. */
    static HtmlBrowser.Factory embeddedFactory() {
        FileObject settings = FileUtil.getConfigFile(
                "Services/Browsers/webviewBrowser.settings");
        if (settings == null) {
            return null; // webview module absent from the platform
        }
        // the settings file's "hidden" attribute is a methodvalue on the
        // platform's own BrowserFactory.isHidden() — reading it RUNS the
        // platform's JavaFX-availability probe; direct getConfigFile
        // bypasses hidden-filtering, so ask explicitly
        if (Boolean.TRUE.equals(settings.getAttribute("hidden"))) {
            return null; // the platform says: no JavaFX on this runtime
        }
        try {
            DataObject dob = DataObject.find(settings);
            InstanceCookie cookie = dob.getLookup().lookup(InstanceCookie.class);
            if (cookie != null
                    && cookie.instanceCreate() instanceof HtmlBrowser.Factory factory) {
                return factory;
            }
        } catch (Exception ex) {
            // fall through: a factory that cannot instantiate is an
            // absent engine, not an error dialog
        }
        return null;
    }

}

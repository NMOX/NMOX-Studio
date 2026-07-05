package org.nmox.studio.rack.search;

import java.util.Locale;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.rack.service.ServingRegistry;

/**
 * Quick Search over what is serving RIGHT NOW: type "serv", a port, or
 * the device's name and the live URL is one Enter away — WEB servings
 * open in the browser, CHAIN servings focus Contract Studio (a devnet
 * URL in a browser tab is useless; the studio speaks its JSON-RPC).
 */
public class LiveServerSearchProvider implements SearchProvider {

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String needle = request.getText() == null ? ""
                : request.getText().toLowerCase(Locale.ROOT);
        if (needle.isBlank()) {
            return;
        }
        for (ServingRegistry.Serving serving : ServingRegistry.getDefault().snapshot()) {
            String haystack = (serving.url() + " " + serving.deviceTitle() + " "
                    + serving.projectDir().getName() + " serving live server")
                    .toLowerCase(Locale.ROOT);
            if (haystack.contains(needle)) {
                String label = "Serving · " + serving.url() + " — "
                        + serving.deviceTitle() + " · " + serving.projectDir().getName();
                boolean more = response.addResult(actionFor(serving), label);
                if (!more) {
                    return;
                }
            }
        }
    }

    private static Runnable actionFor(ServingRegistry.Serving serving) {
        if (serving.kind() == ServingRegistry.Kind.CHAIN) {
            return () -> javax.swing.SwingUtilities.invokeLater(() -> {
                org.openide.windows.TopComponent studio = org.openide.windows.WindowManager
                        .getDefault().findTopComponent("Web3StudioTopComponent");
                if (studio != null) {
                    studio.open();
                    studio.requestActive();
                }
            });
        }
        return () -> {
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop
                        .getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(serving.url()));
                }
            } catch (Exception ignored) {
                // no browser available; the pick just does nothing
            }
        };
    }
}

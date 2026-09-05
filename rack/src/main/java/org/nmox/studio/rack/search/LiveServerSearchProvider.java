package org.nmox.studio.rack.search;

import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.rack.service.ServingRegistry;

/**
 * Quick Search over what is serving RIGHT NOW: type "serv", a port, or
 * the device's name and the live URL is one Enter away — WEB servings
 * open in the in-app Browser, CHAIN servings focus Contract Studio (a devnet
 * URL in a browser tab is useless; the studio speaks its JSON-RPC).
 */
public class LiveServerSearchProvider implements SearchProvider {

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String needle = request.getText();
        if (needle == null || needle.isBlank()) {
            return;
        }
        for (ServingRegistry.Serving serving : ServingRegistry.getDefault().snapshot()) {
            // The URL splits into words, so a bare port ("5173") or host
            // ("localhost") finds the serving without the whole URL.
            if (SearchTerms.matches(needle, serving.url(), serving.deviceTitle(),
                    serving.projectDir().getName(), "serving live server localhost port")) {
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
        // WEB: the in-app Browser, the system browser as the fallback (v2.70.0;
        // the ⇄ chip's twin, one opener for both doors)
        return () -> org.nmox.studio.rack.service.ServingLinks.open(serving.url());
    }
}

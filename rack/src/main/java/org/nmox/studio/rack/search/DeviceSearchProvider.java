package org.nmox.studio.rack.search;

import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.rack.devices.DeviceCatalog;
import org.nmox.studio.rack.service.RackService;

/**
 * Quick Search over the device catalog: type "tunnel" or "docker" in
 * the toolbar search and Enter racks the device and opens the rack -
 * faster than scrolling the palette when you know what you want.
 *
 * <p>Matching runs through {@link SearchTerms}, so "run tests" and
 * "bundle size" work as phrases, and through each device's search
 * vocabulary, so the words people reach for ("coverage", "postgres",
 * "cron") reach the device even when its shelf copy never says them.
 */
public class DeviceSearchProvider implements SearchProvider {

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String needle = request.getText();
        if (needle == null || needle.isBlank()) {
            return;
        }
        for (DeviceCatalog.Entry type : DeviceCatalog.all()) {
            if (SearchTerms.matches(needle,
                    type.title(), type.description(), type.keywords())) {
                boolean more = response.addResult(() -> javax.swing.SwingUtilities.invokeLater(() -> {
                    // a third-party device's build() can throw — never let it
                    // escape onto the EDT from Quick Search (matches the drop
                    // and double-click guards)
                    try {
                        RackService.getDefault().getRack().addDevice(type.create());
                    } catch (Exception | LinkageError ex) {
                        org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                                "Could not add " + type.title() + ": " + ex);
                        return;
                    }
                    org.openide.windows.TopComponent rack = org.openide.windows.WindowManager
                            .getDefault().findTopComponent("RackTopComponent");
                    if (rack != null) {
                        rack.open();
                        rack.requestActive();
                    }
                }), type.title() + "  —  " + type.description());
                if (!more) {
                    return;
                }
            }
        }
    }
}

package org.nmox.studio.rack.search;

import java.util.Locale;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.rack.devices.DeviceCatalog;
import org.nmox.studio.rack.service.RackService;

/**
 * Quick Search over the device catalog: type "tunnel" or "docker" in
 * the toolbar search and Enter racks the device and opens the rack -
 * faster than scrolling the palette when you know what you want.
 */
public class DeviceSearchProvider implements SearchProvider {

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String needle = request.getText() == null ? ""
                : request.getText().toLowerCase(Locale.ROOT);
        if (needle.isBlank()) {
            return;
        }
        for (DeviceCatalog.Entry type : DeviceCatalog.all()) {
            if (type.title().toLowerCase(Locale.ROOT).contains(needle)
                    || type.description().toLowerCase(Locale.ROOT).contains(needle)) {
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

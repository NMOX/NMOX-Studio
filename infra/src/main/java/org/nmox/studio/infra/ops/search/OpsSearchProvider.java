package org.nmox.studio.infra.ops.search;

import java.util.List;
import javax.swing.SwingUtilities;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.infra.ops.OpsHub;
import org.nmox.studio.infra.ops.model.OpsKind;
import org.nmox.studio.infra.ops.ui.HudCanvas;
import org.nmox.studio.infra.ops.ui.OpsHubTopComponent;
import org.openide.util.NbBundle;

/**
 * Quick Search over the Ops Hub: type a server's name, its address, or
 * a service's name and Enter opens the Hub on it — the host in the
 * fleet, the service inside its host.
 *
 * <p>It reads; it never makes. Until the Hub has been opened once in
 * this session there is no Hub, and this provider finds nothing rather
 * than create one from a keystroke (the boot law: nothing of the Hub
 * exists until its window is asked for). What it reads is the Hub's own
 * copy for readers off the event thread, never the live topology.
 */
public class OpsSearchProvider implements SearchProvider {

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        OpsHub hub = OpsHub.peek();
        if (hub == null) {
            return;
        }
        evaluate(request.getText(), hub.findable(), (id, display) -> response.addResult(
                () -> SwingUtilities.invokeLater(() -> OpsHubTopComponent.focusNode(id)), PlainText.escape(display)));
    }

    /**
     * The search itself, off the platform's types (they can only be made
     * inside the platform): the sink returns false to stop, as the SPI's does.
     */
    static void evaluate(String text, List<OpsHub.Findable> entries, java.util.function.BiPredicate<String, String> sink) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (OpsHub.Findable f : entries) {
            if (matches(f, text) && !sink.test(f.id(), display(f))) {
                return;
            }
        }
    }

    /**
     * A host answers to its label, its address and its status word; a
     * service to its label and its host's. Term-based through the one
     * matcher, so "shop web" finds {@code shop-web-1}.
     */
    static boolean matches(OpsHub.Findable f, String needle) {
        return SearchTerms.matches(needle, f.label(), f.address(), f.hostLabel(),
                f.status() == null ? null : HudCanvas.statusWord(f.status()));
    }

    static String display(OpsHub.Findable f) {
        // this package has a hand-written Bundle.properties (the category's name), so its keys live there (v1.79.0)
        if (f.kind() == OpsKind.SERVICE) {
            return NbBundle.getMessage(OpsSearchProvider.class, "OpsSearchProvider_service",
                    f.label(), f.hostLabel() == null ? "?" : f.hostLabel());
        }
        return NbBundle.getMessage(OpsSearchProvider.class, "OpsSearchProvider_host",
                f.label(), f.status() == null ? "" : HudCanvas.statusWord(f.status()), f.address() == null ? "" : f.address());
    }
}

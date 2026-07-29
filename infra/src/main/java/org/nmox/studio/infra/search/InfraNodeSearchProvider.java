package org.nmox.studio.infra.search;

import java.util.Locale;
import javax.swing.SwingUtilities;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.infra.InfraDesignerTopComponent;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Quick Search over the current infra design: type a node's name (or
 * its kind, like "droplet" or "hetzner") in the toolbar search and
 * Enter jumps the Infra Designer to that node and selects it - faster
 * than hunting the canvas when the stack is large.
 */
public class InfraNodeSearchProvider implements SearchProvider {

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        InfraDesignerTopComponent designer = designer();
        if (designer == null) {
            return; // no design open: nothing to search
        }
        evaluate(request.getText(), designer.getGraph(),
                (action, display) -> response.addResult(
                        () -> SwingUtilities.invokeLater(action), display));
    }

    /**
     * The search behavior, seamed off the platform types: the quicksearch
     * SPI's {@code SearchRequest}/{@code SearchResponse} are constructible
     * only inside the platform module, so tests drive this package-private
     * form with a plain sink (returning false = stop, the SPI contract).
     */
    void evaluate(String text, InfraGraph graph,
            java.util.function.BiPredicate<Runnable, String> addResult) {
        String needle = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (needle.isBlank()) {
            return;
        }
        for (InfraNode node : graph.getNodes()) {
            if (matches(node, needle)) {
                String display = node.label + "  —  " + node.kind.getDisplayName()
                        + (node.doId != null ? "  (live)" : "");
                if (!addResult.test(() -> focus(node), display)) {
                    return;
                }
            }
        }
    }

    /** Jumps the (open) designer to the hit; a no-op when it closed meanwhile. */
    private static void focus(InfraNode node) {
        InfraDesignerTopComponent designer = designer();
        if (designer != null) {
            designer.focusNode(node);
        }
    }

    /**
     * Matches a node against the needle by label, kind display name, or
     * enum name - all case-insensitive on both sides, so the matcher is
     * self-contained (the caller need not pre-lowercase).
     */
    static boolean matches(InfraNode node, String needle) {
        String lower = needle.toLowerCase(Locale.ROOT);
        return node.label.toLowerCase(Locale.ROOT).contains(lower)
                || node.kind.getDisplayName().toLowerCase(Locale.ROOT).contains(lower)
                || node.kind.name().toLowerCase(Locale.ROOT).contains(lower);
    }

    /** The open Infra Designer window, or null when it has never been opened. */
    private static InfraDesignerTopComponent designer() {
        TopComponent tc = WindowManager.getDefault()
                .findTopComponent("InfraDesignerTopComponent");
        return tc instanceof InfraDesignerTopComponent designer ? designer : null;
    }
}

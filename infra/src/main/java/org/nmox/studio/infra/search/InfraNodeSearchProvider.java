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
        String needle = request.getText() == null ? ""
                : request.getText().toLowerCase(Locale.ROOT);
        if (needle.isBlank()) {
            return;
        }
        InfraDesignerTopComponent designer = designer();
        if (designer == null) {
            return; // no design open: nothing to search
        }
        InfraGraph graph = designer.getGraph();
        for (InfraNode node : graph.getNodes()) {
            if (matches(node, needle)) {
                String display = node.label + "  —  " + node.kind.getDisplayName()
                        + (node.doId != null ? "  (live)" : "");
                boolean more = response.addResult(
                        () -> SwingUtilities.invokeLater(() -> designer.focusNode(node)),
                        display);
                if (!more) {
                    return;
                }
            }
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

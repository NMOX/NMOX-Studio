package org.nmox.studio.rack.blockstudio.search;

import org.nmox.studio.rack.blockstudio.BlockDoc;
import org.nmox.studio.rack.blockstudio.BlockIO;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.rack.service.RackService;

/**
 * Quick Search (⌘I) into Block Studio: type "blocks", "component", or
 * the component's tag name and Enter opens the studio — the same reach
 * every other studio has had since v1.26/v1.33. The studio publishes a
 * snapshot (tag + piece count) on every regeneration; before it has
 * published anything the provider falls back to reading the aimed
 * project's {@code .nmoxblocks.json} on the Quick Search background
 * thread (never the EDT).
 */
public class BlockSearchProvider implements SearchProvider {

    /** The studio's published snapshot: component tag + piece count. */
    record Snapshot(String tag, int pieces) { }

    private static volatile Snapshot published;

    /** Called by Block Studio (on the EDT) after each regeneration. */
    public static void publish(String tag, int pieces) {
        published = new Snapshot(tag, pieces);
    }

    /** Test seam: forget the published snapshot. */
    static void reset() {
        published = null;
    }

    /** The searchable terms for a snapshot — pure, test-driven. */
    static List<String> terms(Snapshot snap) {
        List<String> t = new ArrayList<>(List.of("blocks", "block studio", "component"));
        if (snap != null && snap.tag() != null && !snap.tag().isBlank()) {
            t.add(snap.tag().toLowerCase(Locale.ROOT));
        }
        return t;
    }

    static boolean matches(String query, Snapshot snap) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            return false;
        }
        return terms(snap).stream().anyMatch(term -> term.contains(q));
    }

    static String label(Snapshot snap) {
        if (snap == null) {
            return "Block Studio — compose a web component";
        }
        return "Block Studio — <" + snap.tag() + "> (" + snap.pieces()
                + (snap.pieces() == 1 ? " piece)" : " pieces)");
    }

    /** The published snapshot, else a best-effort read of the aimed workspace. */
    static Snapshot currentSnapshot() {
        Snapshot snap = published;
        if (snap != null) {
            return snap;
        }
        try {
            File dir = RackService.getDefault().getRack().getProjectDir();
            if (dir != null && BlockIO.workspaceFile(dir).isFile()) {
                BlockDoc doc = BlockIO.load(dir);
                return new Snapshot(doc.root().param("tag"), doc.preorder().size());
            }
        } catch (Exception ignored) {
            // no workspace yet — the generic entry still matches "blocks"
        }
        return null;
    }

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        Snapshot snap = currentSnapshot();
        if (!matches(request.getText(), snap)) {
            return;
        }
        // addResult's boolean says "stop offering results" — honor it
        // (the sibling providers' idiom; the SpotBugs RV gate enforces it)
        if (!response.addResult(() -> java.awt.EventQueue.invokeLater(() -> {
            org.openide.windows.TopComponent tc = org.openide.windows.WindowManager
                    .getDefault().findTopComponent("BlockStudioTopComponent");
            if (tc != null) {
                tc.open();
                tc.requestActive();
            }
        }), label(snap))) {
            return;
        }
    }
}

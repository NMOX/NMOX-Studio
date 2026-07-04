package org.nmox.studio.dbstudio.search;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.TableInfo;
import org.nmox.studio.dbstudio.ui.DbStudioTopComponent;

/**
 * Quick Search (⌘I) over the database workspace: type a connection
 * name, an engine, a host, or a table name and Enter opens DB Studio
 * with the hit selected. Matching is delegated to the pure
 * {@link DbSearchIndex}; the index is a snapshot {@link #publish}ed by
 * DB Studio whenever its specs or its already-fetched containers change
 * — a search NEVER talks to a database. Before DB Studio has published
 * anything (it opens at startup, so this is rare), the provider falls
 * back to the committed spec list on disk; table hits appear once the
 * tree has actually loaded them.
 */
public class DbSearchProvider implements SearchProvider {

    private static volatile DbSearchIndex published;

    /**
     * Called by DB Studio (on the EDT) with its current specs and
     * whatever containers the tree has fetched so far. The index copies
     * both, so the caller's collections stay its own.
     */
    public static void publish(List<ConnectionSpec> specs,
            Map<String, List<TableInfo>> containersBySpecId) {
        published = new DbSearchIndex(specs, containersBySpecId);
    }

    /** Test seam: forget the published snapshot. */
    static void reset() {
        published = null;
    }

    /** The index to search: the published snapshot, else specs from disk. */
    static DbSearchIndex currentIndex() {
        DbSearchIndex index = published;
        if (index != null) {
            return index;
        }
        return new DbSearchIndex(DbWorkspaceIO.load(projectDir()), Map.of());
    }

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String text = request.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        for (DbSearchIndex.Hit hit : currentIndex().matches(text)) {
            if (!response.addResult(() -> open(hit), hit.label())) {
                return;
            }
        }
    }

    private static void open(DbSearchIndex.Hit hit) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            org.openide.windows.TopComponent tc = org.openide.windows.WindowManager
                    .getDefault().findTopComponent("DbStudioTopComponent");
            if (tc == null) {
                return;
            }
            tc.open();
            tc.requestActive();
            if (tc instanceof DbStudioTopComponent studio) {
                if (hit.kind() == DbSearchIndex.Kind.TABLE && hit.table() != null) {
                    studio.selectTable(hit.spec().id(), hit.table().name());
                } else {
                    studio.selectConnection(hit.spec().id());
                }
            }
        });
    }

    /**
     * The aimed project's directory, resolved the same way DB Studio
     * does: the rack's target if it's up, else the user's home. Guarded
     * so tests and a stripped platform (no rack) fall back cleanly.
     */
    private static File projectDir() {
        try {
            File dir = org.nmox.studio.rack.service.RackService.getDefault()
                    .getRack().getProjectDir();
            if (dir != null && dir.isDirectory()) {
                return dir;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // rack unavailable (tests, stripped platform)
        }
        return new File(System.getProperty("user.home"));
    }
}

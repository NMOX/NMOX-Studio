package org.nmox.studio.apiclient.search;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.apiclient.api.WorkspaceIO;
import org.nmox.studio.apiclient.model.ApiModel.Collection;
import org.nmox.studio.apiclient.model.ApiModel.Request;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;
import org.nmox.studio.apiclient.ui.ApiClientTopComponent;

/**
 * Quick Search over the saved API requests: type a request name, an HTTP
 * method, or a fragment of a URL in the toolbar search and Enter opens API
 * Studio with that request selected — faster than hunting the collections
 * tree when you know what you're after. The workspace is read from the aimed
 * project's {@code .nmoxapi.json}, exactly where API Studio persists it.
 */
public class ApiRequestSearchProvider implements SearchProvider {

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String needle = request.getText() == null ? ""
                : request.getText().toLowerCase(Locale.ROOT);
        if (needle.isBlank()) {
            return;
        }
        Workspace ws;
        try {
            ws = WorkspaceIO.load(projectDir());
        } catch (Exception ex) {
            return;
        }
        if (ws == null) {
            return;
        }
        for (Collection c : ws.collections) {
            for (Request r : c.requests) {
                if (!matches(r, needle)) {
                    continue;
                }
                String collectionName = c.name;
                String requestName = r.name;
                boolean more = response.addResult(() -> open(collectionName, requestName),
                        label(r));
                if (!more) {
                    return;
                }
            }
        }
    }

    private static void open(String collectionName, String requestName) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            org.openide.windows.TopComponent tc = org.openide.windows.WindowManager
                    .getDefault().findTopComponent("ApiClientTopComponent");
            if (tc == null) {
                return;
            }
            tc.open();
            tc.requestActive();
            if (tc instanceof ApiClientTopComponent studio) {
                studio.selectRequest(collectionName, requestName);
            }
        });
    }

    private static String label(Request r) {
        return r.method + " " + r.name + "  —  " + r.url;
    }

    /** True when the lowercased needle appears in the request's name, method, or url. */
    static boolean matches(Request r, String needle) {
        return contains(r.name, needle)
                || contains(r.method, needle)
                || contains(r.url, needle);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null
                && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * The matching requests for a needle, across every collection — the pure,
     * UI-free core of {@link #evaluate}, extracted so it can be unit-tested.
     */
    static List<Request> match(Workspace ws, String needle) {
        List<Request> hits = new ArrayList<>();
        if (ws == null || needle == null) {
            return hits;
        }
        String lower = needle.toLowerCase(Locale.ROOT);
        if (lower.isBlank()) {
            return hits;
        }
        for (Collection c : ws.collections) {
            for (Request r : c.requests) {
                if (matches(r, lower)) {
                    hits.add(r);
                }
            }
        }
        return hits;
    }

    /**
     * The aimed project's directory, resolved the same way API Studio does:
     * the rack's target if it's up, else the user's home. Guarded so tests and
     * a stripped platform (no rack) fall back cleanly.
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

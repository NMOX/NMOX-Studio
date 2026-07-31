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
import org.nmox.studio.core.search.SearchTerms;

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
        String text = request.getText();
        if (text == null || text.isBlank()) {
            return; // nothing typed: don't even read the workspace file
        }
        Workspace ws;
        try {
            ws = WorkspaceIO.load(projectDir());
        } catch (Exception ex) {
            return;
        }
        evaluate(text, ws, (action, label) -> response.addResult(action, label));
    }

    /**
     * The search behavior, seamed off the platform types: the quicksearch
     * SPI's {@code SearchRequest}/{@code SearchResponse} are constructible
     * only inside the platform module, so tests drive this package-private
     * form with a plain sink (returning false = stop, the SPI contract).
     */
    void evaluate(String text, Workspace ws,
            java.util.function.BiPredicate<Runnable, String> addResult) {
        String needle = text == null ? "" : text;
        if (needle.isBlank() || ws == null) {
            return;
        }
        for (Collection c : ws.collections) {
            for (Request r : c.requests) {
                if (!matches(r, needle)) {
                    continue;
                }
                String collectionName = c.name;
                String requestName = r.name;
                if (!addResult.test(() -> open(collectionName, requestName), label(r))) {
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

    /**
     * True when every term in the needle matches the request's name,
     * method, or URL. Term-based since v1.215.0, so "post users" finds
     * a POST to /api/users without typing them in URL order.
     */
    static boolean matches(Request r, String needle) {
        return SearchTerms.matches(needle, r.name, r.method, r.url);
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
        String lower = needle;
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
     * the rack's target if it's up, else the user's home. Soft dependency by
     * lookup (ledger 30): a null provider means the rack is absent (tests,
     * stripped platform) and home is the honest fallback.
     */
    private static File projectDir() {
        org.nmox.studio.core.spi.ProjectAim aim =
                org.nmox.studio.core.spi.ProjectAim.find();
        if (aim != null) {
            File dir = aim.projectDir();
            if (dir != null && dir.isDirectory()) {
                return dir;
            }
        }
        return new File(System.getProperty("user.home"));
    }
}

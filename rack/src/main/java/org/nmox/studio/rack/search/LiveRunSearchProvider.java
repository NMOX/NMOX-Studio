package org.nmox.studio.rack.search;

import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.core.spi.LiveRuns;

/**
 * Quick Search over what the product is RUNNING for you (v2.73.0): type
 * "stop", "running", or the run's own words (dev, test, install, the
 * project's name) and Enter stops exactly that run — the ⌘I twin of the
 * Workbench's RUNNING row. Sibling of {@link LiveServerSearchProvider}
 * (what is serving) one registry over.
 */
public class LiveRunSearchProvider implements SearchProvider {

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String needle = request.getText();
        if (needle == null || needle.isBlank()) {
            return;
        }
        for (LiveRuns.Run run : LiveRuns.live()) {
            if (matches(needle, run.label())) {
                String label = "Stop · " + run.label() + " " + LiveRuns.since(run.id());
                if (!response.addResult(() -> stop(run.id(), run.label()), label.trim())) {
                    return;
                }
            }
        }
    }

    /** The label's words, plus the controlled vocabulary a user reaches for. */
    static boolean matches(String needle, String label) {
        return SearchTerms.matches(needle, label, "stop running run kill");
    }

    private static void stop(String id, String label) {
        LiveRuns.Run r = LiveRuns.stop(id);
        org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                r == null ? label + " had already finished" : "Stopped: " + label);
    }
}

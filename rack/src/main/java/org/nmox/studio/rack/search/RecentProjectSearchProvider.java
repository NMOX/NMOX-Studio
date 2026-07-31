package org.nmox.studio.rack.search;

import java.io.File;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.rack.service.RackService;

/**
 * Quick Search over recent projects: type a project's name in the
 * toolbar search (Cmd+I) and Enter re-aims the whole IDE - through the
 * same live-process guard as every other switch path.
 */
public class RecentProjectSearchProvider implements SearchProvider {

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String needle = request.getText();
        if (needle == null || needle.isBlank()) {
            return;
        }
        for (File dir : RackService.getDefault().getRecentProjects()) {
            // The path splits on separators, so "nmox studio" finds
            // ~/vcs/NMOX-Studio without typing the punctuation.
            if (SearchTerms.matches(needle, dir.getName(), dir.getAbsolutePath())) {
                boolean more = response.addResult(
                        () -> javax.swing.SwingUtilities.invokeLater(
                                () -> RackService.getDefault().openProject(dir)),
                        dir.getName() + "  —  " + dir.getAbsolutePath());
                if (!more) {
                    return;
                }
            }
        }
    }
}

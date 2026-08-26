package org.nmox.studio.rack.search;

import java.io.File;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.rack.projectstudio.Experiments;
import org.nmox.studio.rack.projectstudio.LearningSpace;
import org.nmox.studio.rack.service.RackService;

/**
 * Quick Search over the two learning shelves (v2.38.8, the v1.215.0
 * findability law: an unsearchable name is invisible): type an
 * experiment's or learning space's name in ⌘I and Enter aims the
 * studio there. Experiments open QUIETLY — the whole point of the
 * shelf is no recents pollution — while spaces open loudly like any
 * project. Both listings are single {@code listFiles} sweeps of small
 * local homes, cheap enough for the search thread.
 */
public class LearningShelfSearchProvider implements SearchProvider {

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String needle = request.getText();
        if (needle == null || needle.isBlank()) {
            return;
        }
        for (File dir : Experiments.list()) {
            if (SearchTerms.matches(needle, dir.getName(), "experiment")) {
                boolean more = response.addResult(
                        () -> javax.swing.SwingUtilities.invokeLater(
                                () -> RackService.getDefault().openProjectQuietly(dir)),
                        dir.getName() + "  —  experiment, created "
                        + Experiments.info(dir).created());
                if (!more) {
                    return;
                }
            }
        }
        for (File dir : LearningSpace.list()) {
            LearningSpace.Info info = LearningSpace.info(dir);
            if (SearchTerms.matches(needle, dir.getName(), info.name(), "learning space")) {
                boolean more = response.addResult(
                        () -> javax.swing.SwingUtilities.invokeLater(
                                () -> RackService.getDefault().openProject(dir)),
                        dir.getName() + "  —  learning space ("
                        + ("?".equals(info.name()) ? "tutorial" : info.name()) + ")");
                if (!more) {
                    return;
                }
            }
        }
    }
}

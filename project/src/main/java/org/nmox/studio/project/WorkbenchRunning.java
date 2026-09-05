package org.nmox.studio.project;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.nmox.studio.core.spi.LiveRuns;
import org.nmox.studio.core.spi.LiveServings;

/**
 * The Workbench's RUNNING section (v2.73.0): what the product is running
 * for you right now, from the two registries that already know — the
 * ■'s {@link LiveRuns} (every command the IDE started: the ▶, NPM
 * scripts, tests, installs, schematics) and the ⇄ chip's
 * {@link LiveServings} (every server that announced). A run that
 * announced a server carries the URL on its row (the ▶ and the NPM lane
 * register the serving under the run's own id); a serving with no run
 * behind it — a rack device's server — is a row of its own. Pure, so
 * the join and the order are unit tests; the window paints the rows.
 */
final class WorkbenchRunning {

    /** One row: a run (stoppable, id non-null) or a bare serving (openable, url non-null). */
    record Row(String runId, String title, String url) {
        boolean stoppable() {
            return runId != null;
        }

        boolean openable() {
            return url != null;
        }
    }

    private WorkbenchRunning() {
    }

    /** Runs first, in spawn order, each with its serving's URL when one shares its id; then the servings nobody's run owns. */
    static List<Row> rows(List<LiveRuns.Run> runs, List<LiveServings.Serving> servings) {
        List<Row> out = new ArrayList<>();
        Set<String> owned = new LinkedHashSet<>();
        for (LiveRuns.Run r : runs) {
            String url = null;
            for (LiveServings.Serving s : servings) {
                if (s.deviceId().equals(r.id())) {
                    url = s.url();
                    owned.add(s.deviceId());
                    break;
                }
            }
            out.add(new Row(r.id(), r.label(), url));
        }
        for (LiveServings.Serving s : servings) {
            if (!owned.contains(s.deviceId())) {
                out.add(new Row(null, s.deviceTitle(), s.url()));
            }
        }
        return out;
    }

    /** The row's subtitle: the address when it serves, else that it is running. */
    static String subtitle(Row row) {
        return row.openable() ? row.url() : "running";
    }
}

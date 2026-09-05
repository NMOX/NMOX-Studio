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
                if (owns(r.id(), s.deviceId())) {
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

    /**
     * Whether a run owns a serving: the ▶ and the NPM lane register the
     * serving under the run's own id; a rack device registers its run as
     * {@code device:<bus>#n} (v2.74.0) while its serving carries the bus
     * name — so a device run owns the serving of its bus.
     */
    static boolean owns(String runId, String servingId) {
        if (servingId.equals(runId)) {
            return true;
        }
        if (runId.startsWith("device:")) {
            int hash = runId.lastIndexOf('#');
            String bus = hash > 7 ? runId.substring(7, hash) : runId.substring(7);
            return servingId.equals(bus);
        }
        return false;
    }

    /** The row's subtitle: the address when it serves, else since when it runs (v2.73.0), else that it runs. */
    static String subtitle(Row row) {
        return subtitle(row, row.stoppable() ? LiveRuns.since(row.runId()) : "");
    }

    static String subtitle(Row row, String since) {
        if (row.openable()) {
            return since.isEmpty() ? row.url() : row.url() + "  " + since;
        }
        return since.isEmpty() ? "running" : "running " + since;
    }
}

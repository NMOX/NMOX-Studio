package org.nmox.studio.ui.tasks;

import java.io.File;
import java.util.Locale;

import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.core.spi.ProjectAim;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * ⌘I reach into the Task Board (v1.323.0): card titles of the AIMED
 * project match by the same case-insensitive containment the other
 * studio providers use, and choosing a hit opens the Tasks window. The
 * file is read directly (not through the window) so search works even
 * before the tab was ever shown — the window builds on open.
 */
public class TasksSearchProvider implements SearchProvider {

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String q = request.getText();
        if (q == null || q.strip().length() < 2) {
            return;
        }
        ProjectAim aim = ProjectAim.find();
        File dir = aim == null ? null : aim.projectDir();
        if (dir == null || !TasksIO.fileFor(dir).isFile()) {
            return;
        }
        evaluate(q, TasksIO.load(dir),
                (action, label) -> response.addResult(action, label));
    }

    /**
     * The match behavior, seamed off the platform types (v1.324.0): the
     * quicksearch SPI's {@code SearchRequest}/{@code SearchResponse} are
     * constructible only inside the platform module, so a plain
     * {@link BiPredicate} sink (returning false = stop, the SPI contract)
     * lets a unit test prove the reach the shipped-app walk could not —
     * a card title matches case-insensitively by containment and the hit
     * carries its column, exactly as the four sibling studio providers do.
     */
    void evaluate(String query, TaskBoard board,
            java.util.function.BiPredicate<Runnable, String> addResult) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (needle.length() < 2 || board == null) {
            return;
        }
        for (TaskBoard.Column col : board.columns()) {
            for (TaskBoard.Card c : col.cards()) {
                if (!matches(c, needle)) {
                    continue;
                }
                String label = (c.blocked() ? "\u26d4 " : "") + c.title()
                        + (c.label().isEmpty() ? "" : "  [" + c.label() + "]")
                        + " — " + col.name() + " (Tasks)";
                if (!addResult.test(TasksSearchProvider::openTasks, label)) {
                    return;
                }
            }
        }
    }

    /**
     * A card matches on its title, its epic label (searching the epic
     * name finds the epic's cards), or — for the literal query
     * "blocked" — on being blocked, so ⌘I blocked is the keyboard road
     * to the register (v2.7.0).
     */
    private static boolean matches(TaskBoard.Card c, String needle) {
        if (c.title().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (!c.label().isEmpty()
                && c.label().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return c.blocked() && "blocked".equals(needle);
    }

    private static void openTasks() {
        TopComponent tc = WindowManager.getDefault()
                .findTopComponent("TasksTopComponent");
        if (tc != null) {
            tc.open();
            tc.requestActive();
        }
    }
}

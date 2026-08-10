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
        String needle = q.strip().toLowerCase(Locale.ROOT);
        TaskBoard board = TasksIO.load(dir);
        for (TaskBoard.Column col : board.columns()) {
            for (TaskBoard.Card c : col.cards()) {
                if (c.title().toLowerCase(Locale.ROOT).contains(needle)) {
                    String label = c.title() + " — " + col.name() + " (Tasks)";
                    if (!response.addResult(TasksSearchProvider::openTasks, label)) {
                        return;
                    }
                }
            }
        }
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

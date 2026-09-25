package org.nmox.studio.ui.actions;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.core.util.GitRequestFiles;
import org.nmox.studio.rack.service.EditorTabs;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.windows.OnShowing;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Closes a restored tab that holds a file git wrote for its editor.
 *
 * <p>Quitting with a commit message open hands it back to git (the
 * watcher's shutdown hook), but the window system still saves the tab, so
 * every later launch reopened {@code .git/COMMIT_EDITMSG} — by then holding
 * a message git had already used (walked in 3.2: the tab came back showing
 * the NEXT commit's message, written by a {@code git commit -m} in between).
 * Nothing is waiting on such a tab; a request that arrives with the launch
 * is left alone, and so is a tab with unsaved changes.
 *
 * <p>The window system restores only the SELECTED tab of each mode by the
 * time the UI is ready and loads the others afterwards (walked: four tabs
 * open at UI-ready, the restored message tab not among them), so tabs
 * opened in the first {@link #WATCH_MS} are checked too — each on the
 * next EDT turn, by when a request that opened it has registered itself
 * as waiting. A late tab is left alone when it is the ACTIVE one (a file
 * the user just opened is activated; a restored background tab is not)
 * or when any request this session opened it, waiting or not (3.2 eighth
 * review: {@code nmox .git/MERGE_MSG} without {@code -w}, or File ▸ Open
 * in the first half minute, lost its tab).
 */
@OnShowing
public final class StaleGitRequestTabs implements Runnable {

    private static final Logger LOG = Logger.getLogger(StaleGitRequestTabs.class.getName());

    /** How long after the UI is ready a tab the window system restores late is still checked. */
    static final int WATCH_MS = 30_000;

    @Override
    public void run() {
        for (TopComponent tc : TopComponent.getRegistry().getOpened().toArray(new TopComponent[0])) {
            check(tc, false);
        }
        java.beans.PropertyChangeListener late = e -> {
            if (TopComponent.Registry.PROP_TC_OPENED.equals(e.getPropertyName())
                    && e.getNewValue() instanceof TopComponent tc) {
                javax.swing.SwingUtilities.invokeLater(() -> check(tc, true));
            }
        };
        TopComponent.getRegistry().addPropertyChangeListener(late);
        javax.swing.Timer stop = new javax.swing.Timer(WATCH_MS,
                e -> TopComponent.getRegistry().removePropertyChangeListener(late));
        stop.setRepeats(false);
        stop.start();
    }

    /** Where the git-folder test reads the disk: never on the EDT. */
    private static final org.openide.util.RequestProcessor LANE =
            new org.openide.util.RequestProcessor("nmox-stale-git-requests", 1);

    private static void check(TopComponent tc, boolean late) {
        if (!tc.isOpened() || !WindowManager.getDefault().isOpenedEditorTopComponent(tc)) {
            return;
        }
        DataObject dob = tc.getLookup().lookup(DataObject.class);
        FileObject fo = EditorTabs.fileOf(tc);
        File file = fo == null ? null : FileUtil.toFile(fo);
        if (dob == null || !GitRequestFiles.hasRequestName(file)) {
            return;
        }
        // asked on the EDT, now: "the active tab" means the one active when it arrived
        boolean userOpened = late && TopComponent.getRegistry().getActivated() == tc;
        LANE.post(() -> {
            if (GitRequestFiles.isRequestFile(file)) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    boolean asked = EditRequestWatcher.waitedOn(dob) || EditRequestWatcher.requested(dob)
                            || EditRequestWatcher.pending(file);
                    if (tc.isOpened() && shouldClose(true, dob.isModified(), asked || userOpened)) {
                        LOG.log(Level.FINE, "closing the left-over {0}", file);
                        tc.close();
                    }
                });
            }
        });
    }

    /** The rule, with the window system left out: {@code wanted} is a request's file or the user's own open. */
    static boolean shouldClose(File file, boolean modified, boolean wanted) {
        return shouldClose(GitRequestFiles.isRequestFile(file), modified, wanted);
    }

    /** The same rule once the disk has answered whether it is git's file. */
    static boolean shouldClose(boolean requestFile, boolean modified, boolean wanted) {
        return requestFile && !modified && !wanted;
    }
}

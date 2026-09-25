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
 */
@OnShowing
public final class StaleGitRequestTabs implements Runnable {

    private static final Logger LOG = Logger.getLogger(StaleGitRequestTabs.class.getName());

    @Override
    public void run() {
        for (TopComponent tc : TopComponent.getRegistry().getOpened().toArray(new TopComponent[0])) {
            if (!WindowManager.getDefault().isOpenedEditorTopComponent(tc)) {
                continue;
            }
            DataObject dob = tc.getLookup().lookup(DataObject.class);
            FileObject fo = EditorTabs.fileOf(tc);
            File file = fo == null ? null : FileUtil.toFile(fo);
            if (dob != null && shouldClose(file, dob.isModified(), EditRequestWatcher.waitedOn(dob))) {
                LOG.log(Level.FINE, "closing the left-over {0}", file);
                tc.close();
            }
        }
    }

    /** The rule, with the window system left out. */
    static boolean shouldClose(File file, boolean modified, boolean waitedOn) {
        return GitRequestFiles.isRequestFile(file) && !modified && !waitedOn;
    }
}

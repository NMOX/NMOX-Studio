package org.nmox.studio.project;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import org.openide.filesystems.FileUtil;
import org.openide.modules.OnStart;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Listens to the window registry from startup and records every editor
 * file the user touches - the Workbench's recent-files trail keeps
 * filling whether or not the Workbench window is open.
 */
@OnStart
public class RecentFilesTracker implements Runnable, PropertyChangeListener {

    @Override
    public void run() {
        WindowManager.getDefault().invokeWhenUIReady(() ->
                TopComponent.getRegistry().addPropertyChangeListener(this));
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!TopComponent.Registry.PROP_ACTIVATED.equals(evt.getPropertyName())) {
            return;
        }
        TopComponent tc = TopComponent.getRegistry().getActivated();
        if (tc == null || !WindowManager.getDefault().isOpenedEditorTopComponent(tc)) {
            return;
        }
        // the file the tab holds, not its group's primary (3.2)
        org.openide.filesystems.FileObject edited = org.nmox.studio.rack.service.EditorTabs.fileOf(tc);
        if (edited != null) {
            File file = FileUtil.toFile(edited);
            if (file != null) {
                RecentFiles.record(file);
            }
        }
    }
}

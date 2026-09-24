package org.nmox.studio.ui.actions;

import org.openide.windows.OnShowing;

/**
 * The window is up: folders and files macOS handed over during start-up
 * are aimed and opened now, and the open-files handler the platform's
 * applemenu module set in its {@code restored()} is replaced by ours
 * again (see {@link FinderOpen}). Runs on the EDT; on any other OS it
 * only marks the IDE ready, since nothing is ever held there.
 */
@OnShowing
public final class FinderOpenShowing implements Runnable {

    @Override
    public void run() {
        FinderOpen.ready();
    }
}

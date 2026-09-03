package org.nmox.studio.ui.whatsnew;

import org.openide.windows.OnShowing;

/**
 * The first-boot hook's real home (v2.64.1 review): the platform runs
 * this once the main window is SHOWING, so the "What's new since …"
 * dialog can neither pop mid-boot under the window-system restore nor
 * depend on the Welcome tab being open. Zero boot cost on the common
 * path — a preference read and a version compare; the bundled notes are
 * read off the EDT only when there is something to show.
 */
@OnShowing
public final class WhatsNewOnShowing implements Runnable {

    @Override
    public void run() {
        WhatsNew.firstBoot();
    }
}

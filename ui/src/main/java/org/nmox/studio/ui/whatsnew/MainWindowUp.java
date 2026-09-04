package org.nmox.studio.ui.whatsnew;

import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.SwingUtilities;
import org.openide.windows.WindowManager;

/**
 * Runs a dialog-opening task only once the main window is really up. The
 * v2.69.6 walk found the first-boot What's New dialog CREATED but never
 * visible: the {@code @OnShowing} hook fires while the platform is still
 * ordering the main window to the front, and a modal dialog shown against
 * an owner that is not yet on screen stays unmapped on macOS (its window
 * exists for accessibility, has no image, and the app is not blocked). The
 * same dialog from the Help menu shows fine. So: if the main window is
 * showing AND active, run now; otherwise run on its first activation.
 */
final class MainWindowUp {

    private MainWindowUp() {
    }

    /** Pure decision: the owner can host a dialog when it is showing and active. */
    static boolean isUp(boolean showing, boolean active) {
        return showing && active;
    }

    /** EDT only. */
    static void whenUp(Runnable task) {
        Frame mw = WindowManager.getDefault().getMainWindow();
        if (isUp(mw.isShowing(), mw.isActive())) {
            task.run();
            return;
        }
        mw.addWindowListener(new WindowAdapter() {
            private boolean done;

            private void fire() {
                if (!done) {
                    done = true;
                    mw.removeWindowListener(this);
                    SwingUtilities.invokeLater(task);
                }
            }

            @Override
            public void windowActivated(WindowEvent e) {
                fire();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                fire();
            }
        });
    }
}

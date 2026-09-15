package org.nmox.studio.editor.debug;

import java.awt.EventQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.debugger.DebuggerManagerAdapter;
import org.netbeans.api.debugger.Session;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Opens the platform's Sessions window the moment a debug run has more than
 * one session (v2.159.0). Since v2.156.0 every child process and worker a
 * program starts becomes a session of its own — and nothing showed it: the
 * platform's debugger window group declares Sessions {@code open="false"}
 * (the v2.156.0 walk had to open it by hand from Window ▸ Debugging), so a
 * user debugging a forking program never learned the child had stopped. The
 * window appears exactly when it becomes useful — a second session — and
 * never for the single-session case the platform's own default was made for.
 *
 * <p>One listener for the IDE's life, registered lazily from the first
 * launch, so the debugger API is never touched at boot.
 */
final class SessionsWindowOpener extends DebuggerManagerAdapter {

    /** The platform's Sessions window, as its layer names it. */
    static final String SESSIONS_WINDOW = "sessionsView";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private SessionsWindowOpener() {
    }

    /** Registers the one listener; a second call is a no-op. */
    static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            DebuggerManager.getDebuggerManager().addDebuggerListener(new SessionsWindowOpener());
        }
    }

    /** The rule: a run with two or more sessions is worth a window; one is not. */
    static boolean shouldOpen(int sessionCount) {
        return sessionCount >= 2;
    }

    @Override
    public void sessionAdded(Session session) {
        if (shouldOpen(DebuggerManager.getDebuggerManager().getSessions().length)) {
            EventQueue.invokeLater(() -> open(SESSIONS_WINDOW));
        }
    }

    private static void open(String id) {
        TopComponent tc = WindowManager.getDefault().findTopComponent(id);
        if (tc != null) {
            tc.open();
            tc.requestVisible();
        }
    }
}

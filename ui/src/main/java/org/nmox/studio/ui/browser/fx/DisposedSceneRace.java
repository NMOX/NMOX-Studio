package org.nmox.studio.ui.browser.fx;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps one harmless JavaFX race from reaching the user as a red error.
 *
 * <p>Seen on the first walk taken on Windows (3.5), the moment the Browser
 * tab was shown and hidden:
 * <pre>
 * NullPointerException: Cannot invoke "SceneState.update()" because "this.sceneState" is null
 *   at com.sun.javafx.tk.quantum.GlassScene.updateSceneState
 *   at com.sun.javafx.tk.quantum.EmbeddedScene.lambda$setPixelScaleFactors$1
 * </pre>
 * Read from the shipped runtime's bytecode: {@code JFXPanel} queues a
 * pixel-scale update for its scene on the JavaFX thread, the queued task
 * calls {@code updateSceneState()} with no null check, and
 * {@code GlassScene.dispose()} sets {@code sceneState} to null. The window
 * system takes a tab's component out of the hierarchy when another tab is
 * selected, which hides the embedded stage and disposes the scene; a scale
 * update still in the queue then runs against a scene that is gone. Nothing
 * is lost — the scene it wanted to repaint no longer exists, and the next
 * show builds a new one at the right scale.
 *
 * <p>JavaFX hands such a throwable to the JavaFX thread's uncaught-exception
 * handler, which on this platform logs it SEVERE and raises the error
 * notification. This handler sits in front of that one and takes exactly
 * this throwable, by its stack; everything else goes on to the handler that
 * was there.
 */
final class DisposedSceneRace implements Thread.UncaughtExceptionHandler {

    private static final Logger LOG = Logger.getLogger(DisposedSceneRace.class.getName());
    private static final AtomicBoolean SAID = new AtomicBoolean();

    private final Thread.UncaughtExceptionHandler next;

    DisposedSceneRace(Thread.UncaughtExceptionHandler next) {
        this.next = next;
    }

    /** JavaFX thread: puts this handler in front of the thread's own, once. */
    static void installOnThisThread() {
        Thread thread = Thread.currentThread();
        Thread.UncaughtExceptionHandler current = thread.getUncaughtExceptionHandler();
        if (!(current instanceof DisposedSceneRace)) {
            thread.setUncaughtExceptionHandler(new DisposedSceneRace(current));
        }
    }

    /**
     * Whether {@code thrown} is the queued scale update meeting a disposed
     * scene: a NullPointerException raised in {@code updateSceneState},
     * called from {@code EmbeddedScene}'s pixel-scale task. Both frames are
     * required — a null scene state reached any other way is a different
     * defect and stays loud.
     */
    static boolean is(Throwable thrown) {
        if (!(thrown instanceof NullPointerException)) {
            return false;
        }
        StackTraceElement[] stack = thrown.getStackTrace();
        if (stack.length < 2) {
            return false;
        }
        boolean raisedInUpdate = "com.sun.javafx.tk.quantum.GlassScene".equals(stack[0].getClassName())
                && "updateSceneState".equals(stack[0].getMethodName());
        boolean fromScaleTask = "com.sun.javafx.tk.quantum.EmbeddedScene".equals(stack[1].getClassName())
                && stack[1].getMethodName().contains("setPixelScaleFactors");
        return raisedInUpdate && fromScaleTask;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable thrown) {
        if (is(thrown)) {
            // said once at INFO so a log reader can see it happened; after
            // that it is the same sentence again
            LOG.log(SAID.compareAndSet(false, true) ? Level.INFO : Level.FINE,
                    "JavaFX updated the scale of a Browser scene that had just been hidden; nothing was lost");
            return;
        }
        if (next != null) {
            next.uncaughtException(thread, thrown);
        } else {
            Thread.UncaughtExceptionHandler fallback = Thread.getDefaultUncaughtExceptionHandler();
            if (fallback != null) {
                fallback.uncaughtException(thread, thrown);
            }
        }
    }
}

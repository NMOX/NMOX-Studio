package org.nmox.studio.ui.browser.fx;

/**
 * Initializes {@code javafx.scene.Node} on one thread before two can race
 * for it (3.5.5).
 *
 * <p>{@code javafx.scene.Node} and {@code com.sun.javafx.scene.NodeHelper}
 * initialize each other: Node's static initializer hands NodeHelper its
 * accessor, and NodeHelper's forces Node to initialize. Started from one
 * thread, in either order, that is fine. The Browser started them from two.
 * Its constructor queued the engine's construction on the JavaFX thread
 * ({@code new WebView()}, which begins with Node) and returned to the event
 * thread, which added the panel to its window ({@code JFXPanel.addNotify},
 * which begins with NodeHelper). When the two met, each thread held one
 * class's initialization and waited for the other's, for ever, and the
 * event thread was one of them: the window stopped.
 *
 * <p>It was found as a job that never ended. The check that walks an
 * installed release hung on a macOS runner after 3.5.2 and again after
 * 3.5.3, with the Browser tab in front and nothing to read; 3.5.4 gave the
 * walk a leash that takes a thread dump first, and the next hang, on a
 * Linux runner, carried it:
 *
 * <pre>
 * "AWT-EventQueue-0"  at com.sun.javafx.scene.NodeHelper.&lt;clinit&gt;
 *     - waiting on the Class initialization monitor for javafx.scene.Node
 *     at javafx.embed.swing.JFXPanel.addNotify
 * "JavaFX Application Thread"  at javafx.scene.Node.&lt;clinit&gt;
 *     - waiting on the Class initialization monitor for com.sun.javafx.scene.NodeHelper
 *     at org.nmox.studio.ui.browser.fx.FxBrowserPanel.initFx
 * </pre>
 *
 * <p>The cure is to let neither thread find the pair half done. The
 * constructor initializes Node, and NodeHelper inside it, on the event
 * thread before it queues anything on the JavaFX thread.
 */
final class FxClassOrder {

    private FxClassOrder() {
    }

    /** Initializes Node, and NodeHelper inside it, on the calling thread. False where there is no JavaFX. */
    static boolean nodeFirst() {
        return initialized("javafx.scene.Node", FxClassOrder.class.getClassLoader());
    }

    static boolean initialized(String className, ClassLoader loader) {
        try {
            Class.forName(className, true, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false; // a runtime without JavaFX never builds this panel
        }
    }
}

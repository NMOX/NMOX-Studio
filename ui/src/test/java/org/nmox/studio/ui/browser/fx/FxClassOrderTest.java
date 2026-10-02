package org.nmox.studio.ui.browser.fx;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two classes that initialize each other deadlock when two threads begin
 * them at once, and do not when one thread has finished the pair first
 * (3.5.5). That is what froze the window the first time the Browser tab was
 * shown, on some launches and not others.
 */
class FxClassOrderTest {

    private static final String NODE = "org.nmox.studio.ui.browser.fx.order.PairNode";
    private static final String HELPER = "org.nmox.studio.ui.browser.fx.order.PairHelper";

    /** The fixture pair, uninitialized: a loader of its own that shares nothing with the test's. */
    private static ClassLoader freshPair() throws Exception {
        URL testClasses = FxClassOrderTest.class.getProtectionDomain().getCodeSource().getLocation();
        return new URLClassLoader(new URL[] {testClasses}, ClassLoader.getPlatformClassLoader());
    }

    private static Thread begin(String className, ClassLoader loader) {
        Thread t = new Thread(() -> FxClassOrder.initialized(className, loader), "initializes " + className);
        t.setDaemon(true); // the control leaves two of these waiting on each other for good
        t.start();
        return t;
    }

    @Test
    @DisplayName("the control: begun from two threads at once, the pair never finishes")
    void twoThreadsDeadlock() throws Exception {
        ClassLoader pair = freshPair();
        Thread eventThread = begin(HELPER, pair);
        Thread fxThread = begin(NODE, pair);

        eventThread.join(3_000);
        fxThread.join(3_000);
        assertThat(eventThread.isAlive()).as("the thread that began with the helper waits for the node").isTrue();
        assertThat(fxThread.isAlive()).as("the thread that began with the node waits for the helper").isTrue();
    }

    @Test
    @DisplayName("with the node initialized first on one thread, both threads finish")
    void oneThreadFirstThenBoth() throws Exception {
        ClassLoader pair = freshPair();
        assertThat(FxClassOrder.initialized(NODE, pair)).isTrue();

        Thread eventThread = begin(HELPER, pair);
        Thread fxThread = begin(NODE, pair);
        eventThread.join(3_000);
        fxThread.join(3_000);
        assertThat(eventThread.isAlive()).isFalse();
        assertThat(fxThread.isAlive()).isFalse();
    }

    @Test
    @DisplayName("a runtime without the class answers false and throws nothing")
    void absentIsFalse() throws Exception {
        assertThat(FxClassOrder.initialized("javafx.scene.NoSuchNode", freshPair())).isFalse();
    }

    @Test
    @DisplayName("wiring: the Browser initializes Node before it queues anything on the JavaFX thread")
    void theBrowserOrdersIt() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/browser/fx/FxBrowserPanel.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        int constructor = src.indexOf("public FxBrowserPanel(");
        String body = src.substring(constructor, src.indexOf("\n    }\n", constructor));
        int ordered = body.indexOf("FxClassOrder.nodeFirst();");
        int queued = body.indexOf("Platform.runLater(");
        assertThat(ordered).as("the constructor initializes the pair").isPositive();
        assertThat(queued).as("and only then queues the engine's construction").isGreaterThan(ordered);
        assertThat(body.indexOf("Platform.runLater(", queued + 1)).as("the one thing it queues").isNegative();

        String order = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/browser/fx/FxClassOrder.java"),
                StandardCharsets.UTF_8);
        assertThat(order).contains("initialized(\"javafx.scene.Node\"");
    }
}

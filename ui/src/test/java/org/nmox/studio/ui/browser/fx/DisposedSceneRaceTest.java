package org.nmox.studio.ui.browser.fx;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The one JavaFX race the Browser absorbs, and nothing beside it (3.5). */
class DisposedSceneRaceTest {

    private static final String GLASS = "com.sun.javafx.tk.quantum.GlassScene";
    private static final String EMBEDDED = "com.sun.javafx.tk.quantum.EmbeddedScene";

    private static StackTraceElement frame(String cls, String method) {
        return new StackTraceElement(cls, method, cls.substring(cls.lastIndexOf('.') + 1) + ".java", 1);
    }

    /** The throwable the Windows walk logged, frame for frame at the top. */
    private static NullPointerException theRace() {
        NullPointerException npe = new NullPointerException(
                "Cannot invoke \"com.sun.javafx.tk.quantum.SceneState.update()\" because \"this.sceneState\" is null");
        npe.setStackTrace(new StackTraceElement[] {
            frame(GLASS, "updateSceneState"),
            frame(EMBEDDED, "lambda$setPixelScaleFactors$1"),
            frame("com.sun.javafx.tk.quantum.QuantumToolkit", "runWithRenderLock"),
            frame(EMBEDDED, "lambda$setPixelScaleFactors$2"),
            frame("com.sun.javafx.application.PlatformImpl", "lambda$runLater$4")});
        return npe;
    }

    @Test
    @DisplayName("the queued scale update meeting a disposed scene is recognised by both of its frames")
    void recognisesTheRace() {
        assertThat(DisposedSceneRace.is(theRace())).isTrue();
    }

    @Test
    @DisplayName("a null scene state reached any other way stays loud")
    void nothingElseIsAbsorbed() {
        NullPointerException elsewhere = theRace();
        elsewhere.setStackTrace(new StackTraceElement[] {
            frame(GLASS, "updateSceneState"), frame(EMBEDDED, "repaint")});
        assertThat(DisposedSceneRace.is(elsewhere)).as("the same top frame from another caller").isFalse();

        NullPointerException other = theRace();
        other.setStackTrace(new StackTraceElement[] {
            frame("org.nmox.studio.ui.browser.fx.FxBrowserPanel", "initFx"),
            frame(EMBEDDED, "lambda$setPixelScaleFactors$1")});
        assertThat(DisposedSceneRace.is(other)).as("an NPE of our own under the scale task").isFalse();

        IllegalStateException wrongType = new IllegalStateException("no");
        wrongType.setStackTrace(theRace().getStackTrace());
        assertThat(DisposedSceneRace.is(wrongType)).as("another exception from the same frames").isFalse();

        NullPointerException bare = new NullPointerException();
        bare.setStackTrace(new StackTraceElement[0]);
        assertThat(DisposedSceneRace.is(bare)).isFalse();
    }

    @Test
    @DisplayName("the race stops here; every other throwable reaches the handler that was there")
    void othersGoOn() {
        List<Throwable> reached = new ArrayList<>();
        DisposedSceneRace handler = new DisposedSceneRace((thread, thrown) -> reached.add(thrown));
        handler.uncaughtException(Thread.currentThread(), theRace());
        assertThat(reached).as("the race is not passed on").isEmpty();

        RuntimeException real = new RuntimeException("a real failure on the JavaFX thread");
        handler.uncaughtException(Thread.currentThread(), real);
        assertThat(reached).containsExactly(real);
    }

    @Test
    @DisplayName("installing twice on one thread wraps once, and the Browser installs it first on the JavaFX thread")
    void installedOnceAndFirst() throws Exception {
        Throwable[] seen = new Throwable[1];
        Thread.UncaughtExceptionHandler[] installed = new Thread.UncaughtExceptionHandler[2];
        Thread t = new Thread(() -> {
            DisposedSceneRace.installOnThisThread();
            installed[0] = Thread.currentThread().getUncaughtExceptionHandler();
            DisposedSceneRace.installOnThisThread();
            installed[1] = Thread.currentThread().getUncaughtExceptionHandler();
            throw new IllegalStateException("goes on to the handler that was there");
        });
        t.setUncaughtExceptionHandler((thread, thrown) -> seen[0] = thrown);
        t.start();
        t.join(5000);
        assertThat(installed[0]).isInstanceOf(DisposedSceneRace.class).isSameAs(installed[1]);
        assertThat(seen[0]).isInstanceOf(IllegalStateException.class);

        String panel = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/browser/fx/FxBrowserPanel.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        int init = panel.indexOf("private void initFx() {");
        int install = panel.indexOf("DisposedSceneRace.installOnThisThread();", init);
        int firstWork = panel.indexOf("webView = new WebView();", init);
        assertThat(install).as("installed inside initFx").isGreaterThan(init);
        assertThat(install).as("before the first JavaFX object is made").isLessThan(firstWork);
    }
}

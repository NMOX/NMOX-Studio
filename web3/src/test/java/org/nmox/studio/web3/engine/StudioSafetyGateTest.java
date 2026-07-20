package org.nmox.studio.web3.engine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gates for the v1.100.0 Contract Studio fixes that live in the
 * pure-Swing TopComponent plain tests can't drive:
 * <ul>
 * <li>SEND and Deploy confirm before broadcasting to a non-loopback
 * endpoint, with the safe button as the default (the v1.98.0
 * dialog-safety idiom — {@code Confirmation} hard-codes OK);</li>
 * <li>the workspace reload READ rides RP, never the EDT;</li>
 * <li>a stopped watch revokes cursor ownership from any in-flight
 * tick (generation guard), and the tick ranges come from the pure
 * {@link WatchCursor} so both lanes stay clamped.</li>
 * </ul>
 */
class StudioSafetyGateTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/web3/ui/Web3StudioTopComponent.java"),
                StandardCharsets.UTF_8);
    }

    private static String method(String src, String signature) {
        int m = src.indexOf(signature);
        assertThat(m).as(signature + " exists").isPositive();
        return src.substring(m, src.indexOf("\n    }", m));
    }

    @Test
    @DisplayName("SEND and Deploy both pass the remote-broadcast confirmation")
    void broadcastsAreConfirmed() throws Exception {
        String src = source();
        assertThat(method(src, "private void send(InteractSession"))
                .contains("confirmRemoteBroadcast(");
        assertThat(method(src, "private void deploy(InteractSession"))
                .contains("confirmRemoteBroadcast(");
    }

    @Test
    @DisplayName("The broadcast confirm defaults to No and exempts only provable loopback")
    void confirmDefaultsToSafe() throws Exception {
        String body = method(source(), "private boolean confirmRemoteBroadcast(");
        assertThat(body)
                .as("the v1.98.0 idiom: full ctor, NO_OPTION as initialValue")
                .contains("NotifyDescriptor.YES_OPTION, NotifyDescriptor.NO_OPTION},")
                .contains("NotifyDescriptor.NO_OPTION);")
                .doesNotContain("new NotifyDescriptor.Confirmation(");
        assertThat(body)
                .as("loopback devnets stay frictionless; everything else confirms")
                .contains("isLoopbackEndpoint()");
    }

    @Test
    @DisplayName("The workspace reload read rides RP; the EDT only applies the result")
    void reloadReadsOffTheEdt() throws Exception {
        String src = source();
        String reload = method(src, "private void reloadWorkspace()");
        assertThat(reload)
                .as("the loadGuarded file read is posted to the worker")
                .contains("RP.post(")
                .contains("Web3WorkspaceIO.loadGuarded(");
        assertThat(reload.indexOf("RP.post("))
                .as("the read happens INSIDE the posted task, not before it")
                .isLessThan(reload.indexOf("Web3WorkspaceIO.loadGuarded("));
        assertThat(reload)
                .as("overlapping reloads resolve newest-wins")
                .contains("++reloadSeq")
                .contains("seq != reloadSeq");
    }

    @Test
    @DisplayName("stopWatch revokes cursor ownership and the tick honors it")
    void watchGenerationGuard() throws Exception {
        String src = source();
        assertThat(method(src, "private void stopWatch()"))
                .contains("watchGeneration++");
        String tick = method(src, "private void watchTick()");
        assertThat(tick)
                .as("a superseded tick abandons its cursor writes")
                .contains("gen != watchGeneration");
        assertThat(tick)
                .as("both fetch lanes ride the pure clamped plan")
                .contains("WatchCursor.plan(");
    }
}

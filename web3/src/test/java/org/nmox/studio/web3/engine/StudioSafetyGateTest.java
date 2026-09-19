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

    private static String runnerSource() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/web3/engine/WatchRunner.java"),
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
    @DisplayName("WatchRunner.stop revokes cursor ownership and every lane asks its session")
    void watchGenerationGuard() throws Exception {
        // the orchestration left the TopComponent in v2.186.0 (ledger 113);
        // the law did not move, so neither did this gate — it reads the runner
        String src = runnerSource();
        String stop = method(src, "public void stop()");
        assertThat(stop)
                .contains("generation.incrementAndGet()")
                .as("STOP closes the live subscription with the session")
                .contains(".retire()");
        assertThat(stop.indexOf("generation.incrementAndGet()"))
                .as("the bump happens BEFORE the retire, so nothing in flight can re-attach")
                .isLessThan(stop.indexOf(".retire()"));
        // the guard itself is behavior-tested in WatchReconcilerTest and driven
        // end to end in WatchRunnerTest; these pin that every lane routes through it
        String tick = method(src, "private void tick(WatchReconciler session)");
        assertThat(tick)
                .as("both fetch lanes ride the pure clamped plan (WatchCursor.plan inside)")
                .contains("session.pollPlan(")
                .as("a superseded tick abandons its cursor writes")
                .contains("session.commitPoll(");
        String head = method(src, "private void streamHead(WatchReconciler session, long head)");
        assertThat(head).contains("session.onHead(").contains("session.commitHead(");
        String log = method(src, "private boolean feedLog(");
        assertThat(log).contains("session.acceptLog(");
        assertThat(method(src, "private void open("))
                .as("a socket is only kept once the session accepts it")
                .contains("session.attach(")
                .contains("source.wsUrl()");
        assertThat(method(src, "private void feedBlock("))
                .as("a dying tick's row never lands in the next session's feed")
                .contains("session.current()");
    }

    @Test
    @DisplayName("The Watch orchestration carries no Swing; the pane does its own hop")
    void watchRunnerHoldsNoSwing() throws Exception {
        assertThat(runnerSource())
                .as("ledger 113: WatchRunner is engine code, testable without a display")
                .doesNotContain("javax.swing")
                .doesNotContain("SwingUtilities")
                .doesNotContain("java.awt");
    }
}

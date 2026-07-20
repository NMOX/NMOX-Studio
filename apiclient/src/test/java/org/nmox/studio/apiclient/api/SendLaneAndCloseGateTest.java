package org.nmox.studio.apiclient.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gates for the ledger-52 UI wiring (v1.99.0), on the
 * pure-Swing TopComponent that plain tests can't drive:
 * <ul>
 * <li>sends ride their own INTERRUPTIBLE lane, never the shared
 * two-slot housekeeping RP (two hung sends used to wedge re-aim
 * follows and workspace loads until the request timeouts fired);</li>
 * <li>the pretty re-parse of the response body happens on the worker,
 * not the EDT (a megabyte body froze the paint thread; a deeply-nested
 * one threw StackOverflowError through it);</li>
 * <li>componentClosed saves only when dirty — the unconditional save
 * round-tripped a newer file through the unknown-key-dropping parser
 * on a no-op open/close.</li>
 * </ul>
 */
class SendLaneAndCloseGateTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Sends ride a dedicated interruptible RP, and Cancel reaches the in-flight task")
    void sendsHaveTheirOwnInterruptibleLane() throws Exception {
        String src = source();
        assertThat(src)
                .as("the send lane exists and is created interruptible (the third ctor arg "
                        + "is what makes Task.cancel() a real Cancel)")
                .contains("new RequestProcessor(\"API Studio Send\", 4, true)");
        int m = src.indexOf("private void send()");
        assertThat(m).isPositive();
        String body = src.substring(m, src.indexOf("\n    private ", m + 10));
        assertThat(body)
                .as("the send worker posts to the send lane")
                .contains("SEND_RP.post(");
        assertThat(body.replace("SEND_RP.post", ""))
                .as("no send work rides the shared housekeeping RP")
                .doesNotContain("RP.post(");
        assertThat(body)
                .as("an in-flight send is cancellable")
                .contains("inFlight.cancel()");
    }

    @Test
    @DisplayName("The pretty re-parse rides the worker; the EDT path never calls pretty()")
    void prettyHappensOffTheEdt() throws Exception {
        String src = source();
        int m = src.indexOf("private void send()");
        String sendBody = src.substring(m, src.indexOf("\n    private ", m + 10));
        assertThat(sendBody)
                .as("the guarded pretty is computed on the send worker")
                .contains("WorkspaceIO.prettyForDisplay(");

        int s = src.indexOf("private void showResponse(");
        assertThat(s).isPositive();
        String showBody = src.substring(s, src.indexOf("\n    private ", s + 10));
        assertThat(showBody)
                .as("showResponse (EDT) renders the precomputed text — no re-parse on paint")
                .doesNotContain("WorkspaceIO.pretty(")
                .doesNotContain("prettyForDisplay(");
    }

    @Test
    @DisplayName("componentClosed saves only when the debounce says dirty")
    void closeSavesOnlyWhenDirty() throws Exception {
        String src = source();
        int m = src.indexOf("public void componentClosed()");
        assertThat(m).isPositive();
        String body = src.substring(m, src.indexOf("\n    }", m));
        assertThat(body)
                .as("dirty = an armed debounce (the onProjectReaimed idiom)")
                .contains("boolean dirty = saveDebounce.isRunning();");
        assertThat(body)
                .as("the save is guarded, not unconditional")
                .contains("if (dirty) {");
    }
}

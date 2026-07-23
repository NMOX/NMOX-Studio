package org.nmox.studio.editor.debug;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 55 closed — source gates for the fixes whose behavior lives in
 * process/window plumbing a unit test can't cheaply spawn: the Prettier
 * timeout kills the TREE with a daemon drain (M2), the debug free-port
 * probe binds loopback only (L1), the completion identifier harvest is
 * windowed (L4), and interrupted Chrome profiles are reaped at shutdown
 * (L5). The behavioral halves (M1 pair reap, L3 malformed refusal, M2
 * cap refusal) live in DapProxyTest / PrettierFormatterTest.
 */
class Ledger55GateTest {

    /** Line endings normalized — anchored slicing breaks on a CRLF
     * (Windows) checkout otherwise (the v1.103.0 lesson). */
    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }

    @Test
    @DisplayName("Prettier's timeout kills the process TREE and the drain is a daemon (M2)")
    void prettierTimeoutKillsTree() throws Exception {
        String src = source("src/main/java/org/nmox/studio/editor/format/PrettierFormatter.java");
        int timeout = src.indexOf("if (!process.waitFor(TIMEOUT_MS");
        assertThat(timeout).as("the timeout branch exists").isPositive();
        assertThat(src.substring(timeout, timeout + 400))
                .as("a node wrapper's grandchild survives destroyForcibly(); "
                        + "the timeout path must sweep the whole tree")
                .contains("ProcessSupport.killTreeAndWait(process");
        assertThat(src)
                .as("the stdout drain must never pin JVM shutdown")
                .contains("drain.setDaemon(true)");
        assertThat(src)
                .as("the drain reads a bounded prefix, then discards to EOF")
                .contains("readNBytes(OUTPUT_CAP_BYTES + 1)");
    }

    @Test
    @DisplayName("The debug free-port probe binds loopback, never every interface (L1)")
    void freePortBindsLoopback() throws Exception {
        String src = source("src/main/java/org/nmox/studio/editor/debug/DapDebugAction.java");
        int probe = src.indexOf("private static int freePort()");
        assertThat(probe).as("freePort exists").isPositive();
        assertThat(src.substring(probe, probe + 400))
                .as("the probe listener must be loopback-bound like JsDebugServer's")
                .contains("InetAddress.getLoopbackAddress()");
    }

    @Test
    @DisplayName("The completion identifier harvest lexes a window, not the whole file (L4)")
    void completionScanIsWindowed() throws Exception {
        String src = source(
                "src/main/java/org/nmox/studio/editor/completion/JavaScriptCompletionProvider.java");
        int harvest = src.indexOf("private void addDocumentIdentifiers(");
        assertThat(harvest).as("the harvest method exists").isPositive();
        String body = src.substring(harvest, src.indexOf("\n        }", harvest));
        assertThat(body)
                .as("the re-lex is bounded by the scan window, not doc.getLength() from 0")
                .contains("SCAN_WINDOW_CHARS")
                .contains("doc.getText(windowStart, windowEnd - windowStart)");
        assertThat(src)
                .as("the window is generous enough that ordinary files see no change")
                .contains("SCAN_WINDOW_CHARS = 200_000");
    }

    @Test
    @DisplayName("Interrupted-session Chrome profiles are reaped by a shutdown hook (L5)")
    void chromeProfilesReapedOnShutdown() throws Exception {
        String src = source("src/main/java/org/nmox/studio/editor/debug/BrowserDebugAction.java");
        assertThat(src)
                .as("live profiles are tracked in a set the hook sweeps")
                .contains("LIVE_PROFILES.add(profile)")
                .contains("LIVE_PROFILES.remove(profile)");
        int hook = src.indexOf("addShutdownHook");
        assertThat(hook).as("the reaper hook is registered").isPositive();
        assertThat(src.substring(hook, hook + 200))
                .as("the hook deletes every profile still live at exit")
                .contains("LIVE_PROFILES.forEach(BrowserDebugAction::deleteRecursively)");
    }
}

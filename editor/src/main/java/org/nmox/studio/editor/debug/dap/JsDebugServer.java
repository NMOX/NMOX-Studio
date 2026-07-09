package org.nmox.studio.editor.debug.dap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.nmox.studio.core.process.ProcessSupport;

/**
 * Owns one running js-debug DAP server ({@code node dapDebugServer.js
 * <port> 127.0.0.1}) for the lifetime of one debug session.
 *
 * Lifecycle rules this class exists to enforce: the server never exits on
 * its own, and the debuggee node process is a DESCENDANT of the server —
 * so teardown must kill the whole tree, and a force-quit IDE must not
 * orphan a chain (static reaper hook, removed again on clean stop, the
 * rack's reaper idiom).
 */
public final class JsDebugServer {

    private static final Logger LOG = Logger.getLogger(JsDebugServer.class.getName());
    private static final String READY_MARKER = "Debug server listening at";
    private static final int READY_TIMEOUT_SECONDS = 10;

    /** Live servers the JVM shutdown hook reaps; emptied by clean stop(). */
    private static final Set<JsDebugServer> LIVE = ConcurrentHashMap.newKeySet();
    static {
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> LIVE.forEach(s -> ProcessSupport.killTree(s.process)),
                        "nmox-jsdebug-reaper"));
    }

    private final Process process;
    private final int port;
    private volatile boolean stopped;

    private JsDebugServer(Process process, int port) {
        this.process = process;
        this.port = port;
    }

    /**
     * Spawns the server and blocks (bounded) until it prints its READY
     * line. On timeout or early exit the process tree is killed and an
     * IOException names what happened.
     */
    public static JsDebugServer start(File dapDebugServerJs) throws IOException, InterruptedException {
        int port = freePort();
        ProcessBuilder pb = ProcessSupport.builder(List.of(
                "node", dapDebugServerJs.getAbsolutePath(),
                String.valueOf(port), "127.0.0.1"));
        pb.directory(dapDebugServerJs.getParentFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();

        // The wait ends on EITHER outcome: the READY line, or the output
        // stream reaching EOF (the process died). Waiting only for READY
        // makes a dead adapter cost the full timeout before it reports the
        // failure it already knows about.
        AtomicBoolean sawReady = new AtomicBoolean();
        CountDownLatch settled = new CountDownLatch(1);
        Thread pump = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains(READY_MARKER)) {
                        sawReady.set(true);
                        settled.countDown();
                    }
                    // the adapter's output is untrusted text: strip control
                    // characters so it cannot forge extra log records
                    LOG.log(Level.FINE, "js-debug: {0}", sanitize(line));
                }
            } catch (IOException ex) {
                LOG.log(Level.FINE, "js-debug output pump ended", ex);
            } finally {
                settled.countDown(); // EOF: the adapter is gone, stop waiting
            }
        }, "nmox-jsdebug-pump");
        pump.setDaemon(true);
        pump.start();

        boolean settledInTime;
        try {
            settledInTime = settled.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            ProcessSupport.killTree(p);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for js-debug to start", ex);
        }
        if (!sawReady.get()) {
            ProcessSupport.killTree(p);
            if (!settledInTime) {
                throw new IOException(
                        "js-debug did not become ready within " + READY_TIMEOUT_SECONDS + "s");
            }
            String detail = p.waitFor(2, TimeUnit.SECONDS)
                    ? "exit " + p.exitValue() : "no exit status";
            throw new IOException("js-debug exited immediately (" + detail
                    + ") — is Node.js installed and the adapter intact?");
        }
        JsDebugServer server = new JsDebugServer(p, port);
        LIVE.add(server);
        return server;
    }

    public int port() {
        return port;
    }

    /** Idempotent. Descendants first — the debuggee must not outlive us. */
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        LIVE.remove(this);
        ProcessSupport.killTree(process);
    }

    /**
     * The adapter's output is untrusted text: strip control characters so a
     * crafted line cannot forge extra log records. The regex already removes
     * CR and LF; the explicit char replaces follow because they are the only
     * form find-sec-bugs recognizes as a CRLF sanitizer.
     */
    private static String sanitize(String line) {
        return line.replaceAll("\\p{Cntrl}", "").replace('\r', ' ').replace('\n', ' ');
    }

    /** Loopback-bound: the adapter is local, and the probe socket must not
     *  be reachable from the network even for the instant it is open. */
    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return s.getLocalPort();
        }
    }
}

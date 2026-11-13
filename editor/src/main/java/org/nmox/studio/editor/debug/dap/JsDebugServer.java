package org.nmox.studio.editor.debug.dap;

import org.nmox.studio.core.util.Threads;
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
 *
 * <p>The adapter runs under the user's own {@code node}, so the adapter's
 * Node floor is the user's floor ({@link #MIN_NODE_MAJOR}): a Node below
 * it is refused by name ({@link NodeTooOld}) before anything is started.
 */
public final class JsDebugServer {

    private static final Logger LOG = Logger.getLogger(JsDebugServer.class.getName());
    private static final String READY_MARKER = "Debug server listening at";

    /**
     * The oldest Node major the vendored adapter LOADS under. Measured for
     * js-debug v1.140.1: under Node 18.20.8 the bundle dies while loading
     * ({@code SyntaxError: Invalid flags supplied to RegExp constructor
     * 'v'} — the RegExp {@code v} flag arrived with Node 20), where
     * v1.117.0 started. Its bundled HTTP client (got 16) declares Node 22;
     * 22.9.0 is what the real-adapter tests ran on, and 20 and 21 were not
     * measured — so the refusal covers what is certain and the user guide
     * names 22. Re-measure on every adapter bump (the NOTICE beside the
     * adapter says how).
     */
    public static final int MIN_NODE_MAJOR = 20;
    private static final java.time.Duration NODE_VERSION_TIMEOUT = java.time.Duration.ofSeconds(5);
    private static final java.util.regex.Pattern NODE_VERSION =
            java.util.regex.Pattern.compile("^v?(\\d{1,4})\\.\\d{1,6}\\.\\d{1,6}");

    /**
     * The user's Node is older than the vendored adapter can load under.
     * Carries the two facts so the caller says the sentence in the reader's
     * language; the message here is the log's.
     */
    public static final class NodeTooOld extends IOException {
        private static final long serialVersionUID = 1L;
        private final String found;

        public NodeTooOld(String found) {
            super("Node.js " + found + " is too old for the bundled js-debug adapter, which needs Node.js "
                    + MIN_NODE_MAJOR + " or newer");
            this.found = found;
        }

        /** The version {@code node --version} printed, as printed ({@code v18.20.8}). */
        public String found() {
            return found;
        }

        /** The oldest major the adapter starts under. */
        public int needed() {
            return MIN_NODE_MAJOR;
        }
    }

    /**
     * The major of what {@code node --version} printed, or -1 when the
     * text is not a Node version. Pure; the first line is the answer.
     */
    static int nodeMajor(String versionOutput) {
        if (versionOutput == null) {
            return -1;
        }
        java.util.regex.Matcher m = NODE_VERSION.matcher(versionOutput.strip());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /**
     * Refuses a Node the adapter cannot load under, by name, BEFORE the
     * adapter is spawned — without this the user saw "js-debug exited
     * immediately (exit 1)" and a stack trace in a FINE log record. Only a
     * definite answer refuses: a {@code node} that is missing, slow, or
     * prints something that is not a version is left to the launch itself,
     * which already reports those.
     */
    private static void requireNode(String node, File dir) throws NodeTooOld {
        ProcessSupport.BoundedResult version;
        try {
            version = ProcessSupport.runBounded(List.of(node, "--version"), dir, NODE_VERSION_TIMEOUT);
        } catch (IOException ex) {
            LOG.log(Level.FINE, "node --version could not be run; the launch will say why", ex);
            return;
        }
        if (!version.ok()) {
            return;
        }
        java.util.regex.Matcher m = NODE_VERSION.matcher(version.stdout().strip());
        if (m.find() && Integer.parseInt(m.group(1)) < MIN_NODE_MAJOR) {
            throw new NodeTooOld(m.group()); // the matched version alone, never the rest of the output
        }
    }
    private static final int READY_TIMEOUT_SECONDS = 30; // until-ready wait, not a sleep: costs
    // nothing when node starts fast. 10s false-failed on cold/loaded
    // machines (antivirus-scanned windows runners; first-boot laptops)
    // while the adapter itself was healthy.

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
     * IOException names what happened; a Node older than the adapter loads
     * under is a {@link NodeTooOld}, thrown before anything is spawned.
     */
    public static JsDebugServer start(File dapDebugServerJs) throws IOException, InterruptedException {
        return start(dapDebugServerJs, "node");
    }

    /** Test seam: {@code node} is the command that stands for the user's Node. */
    static JsDebugServer start(File dapDebugServerJs, String node) throws IOException, InterruptedException {
        requireNode(node, dapDebugServerJs.getParentFile());
        int port = freePort();
        ProcessBuilder pb = ProcessSupport.builder(List.of(
                node, dapDebugServerJs.getAbsolutePath(),
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
        Thread pump = Threads.daemon(() -> {
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

    /**
     * Idempotent. Descendants first — the debuggee must not outlive us —
     * and stop() does not return until the tree is confirmed dead (bounded):
     * on Windows a dying node still locks its open scripts and cwd, and the
     * caller's next move is typically to delete exactly those files.
     */
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        LIVE.remove(this);
        ProcessSupport.killTreeAndWait(process, java.time.Duration.ofSeconds(5));
    }

    /** Test seam: whether the adapter process is still alive. */
    boolean processAlive() {
        return process.isAlive();
    }

    /** Test seam: the adapter's handle, so tests can assert the whole
     *  descendant tree (the debuggee, the launched browser) really died. */
    ProcessHandle processHandle() {
        return process.toHandle();
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

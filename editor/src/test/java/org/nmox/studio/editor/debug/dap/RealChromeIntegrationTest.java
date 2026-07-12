package org.nmox.studio.editor.debug.dap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpServer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.nmox.studio.core.process.ProcessSupport;
import org.nmox.studio.editor.debug.BrowserLocator;
import org.openide.util.BaseUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The browser-debug recon transcript as a permanent regression test: the
 * REAL vendored js-debug adapter launching REAL headless Chrome against a
 * real HTTP fixture, this test playing the NetBeans client through the
 * proxy. Skips (with a message) where no Chromium-family browser is
 * installed — CI runners carry Chrome on all three OSes.
 *
 * Pins the three recon findings the product relies on: the page target's
 * startDebugging arrives once on the parent link and the proxy's child
 * dance suffices for browser-side breakpoints; source paths map back
 * through webRoot to the real file; and a client disconnect reaps the
 * browser through js-debug's {@code cleanUp: wholeBrowser} default — on
 * macOS and Linux that disconnect ALONE suffices, but on Windows js-debug
 * renames the launched browser process, snapping the parent-PID chain its
 * forceful cleanup (and our descendants() walk) relies on, so the browser
 * outlives disconnect there. The PRODUCT never leans on disconnect: every
 * teardown path runs {@code JsDebugServer.stop() -> killTreeAndWait} as the
 * backstop, which is the reaper the disconnect assertion below actually
 * pins on every OS (ledger 40).
 */
// Sized so a worst-case green run (a 120s-window stopped await that lands
// late, then the 30s disconnect poll) still finishes inside it, and a single
// wedged await fails via its own diagnostic AssertionError, never this
// blunter kill.
@Timeout(240)
class RealChromeIntegrationTest {

    /** The vendored adapter, straight from the module's release dir. */
    private static final File SERVER_JS = new File(
            "src/main/release/jsdebug/js-debug/src/dapDebugServer.js").getAbsoluteFile();

    private HttpServer http;
    private JsDebugServer server;
    private DapProxy proxy;
    private Path profile;
    private AdapterLogTap adapterLog;

    @AfterEach
    void tearDown() throws IOException {
        if (adapterLog != null) {
            adapterLog.close();
        }
        if (proxy != null) {
            proxy.close();
        }
        if (server != null) {
            server.stop(); // confirms the adapter+Chrome tree dead (bounded)
        }
        if (http != null) {
            http.stop(0);
        }
        // only after the tree is confirmed dead: Chrome holds locks inside
        // its profile until it exits (the Windows @TempDir lesson — which is
        // also why the profile lives OUTSIDE @TempDir, in our own temp dir)
        if (profile != null) {
            try (var walk = Files.walk(profile)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    @DisplayName("breakpoint in browser-side JS: hit in headless Chrome, stack mapped to the file, Stop kills the browser tree")
    void shouldHitBrowserBreakpointEndToEnd(@TempDir Path tmp) throws Exception {
        assumeTrue(nodePresent(), "node not installed");
        File browser = BrowserLocator.find();
        assumeTrue(browser != null, "no Chromium-family browser installed — skipping browser debug E2E");
        assertThat(SERVER_JS).as("vendored adapter present").exists();

        // toRealPath: macOS hands @TempDir a /var symlink but js-debug
        // reports canonical /private/var paths back — compare like for like
        Path dir = tmp.toRealPath();
        Files.writeString(dir.resolve("index.html"), """
                <!doctype html>
                <html><body>
                <h1>e2e</h1>
                <script src="app.js"></script>
                </body></html>
                """, StandardCharsets.UTF_8);
        Path appJs = dir.resolve("app.js");
        Files.writeString(appJs, """
                let count = 0;
                function tick() {
                  count = count + 1;
                  console.log('tick ' + count);
                }
                setInterval(tick, 250);
                """, StandardCharsets.UTF_8);
        String url = serveFixture(dir);
        profile = Files.createTempDirectory("nmox-chrome-e2e-profile-");

        // the adapter's own output (Chrome launch errors land there) is the
        // other half of any post-mortem — tap it before the adapter starts
        adapterLog = new AdapterLogTap();
        server = JsDebugServer.start(SERVER_JS);
        proxy = DapProxy.start(server.port(), () -> { });
        Client nb = new Client(proxy.clientInput(), proxy.clientOutput(),
                adapterLog::dump);

        nb.request("initialize", new JSONObject()
                .put("clientID", "test").put("adapterID", "test")
                .put("pathFormat", "path")
                .put("linesStartAt1", true).put("columnsStartAt1", true));
        nb.awaitResponse("initialize");

        nb.request("launch", new JSONObject()
                .put("type", "pwa-chrome").put("request", "launch")
                .put("name", "e2e")
                .put("url", url)
                .put("webRoot", dir.toString())
                .put("runtimeExecutable", browser.getAbsolutePath())
                // headless in CI only; the product launches headed — the
                // user watches the page they are debugging
                .put("runtimeArgs", new JSONArray().put("--headless=new"))
                // js-debug's own give-up ceiling for attaching to the browser
                // it just spawned (default 10000ms). Two loaded ubuntu runners
                // blew through the default — Chrome cold-started slower than
                // 10s and js-debug failed the launch with "Could not attach to
                // main target" (actions/runs/29180149462 attempt 1,
                // actions/runs/29177513763 attempt 1; both post-mortems named
                // it via the transcript + adapter tap). Repro is mechanical:
                // shrink this below Chrome's startup and the identical failure
                // fires locally. NOT a sleep — attach completes the instant the
                // target appears (~1.5s green) — so the ceiling matches the
                // 120s cold-start window the 'stopped' await below already
                // grants for exactly this reason.
                .put("timeout", 120_000)
                .put("userDataDir", profile.toString()));

        nb.awaitEvent("initialized");
        nb.request("setBreakpoints", new JSONObject()
                .put("source", new JSONObject()
                        .put("path", appJs.toString()))
                .put("breakpoints", new JSONArray()
                        .put(new JSONObject().put("line", 3))));
        nb.awaitResponse("setBreakpoints");
        nb.request("configurationDone", new JSONObject());

        // Chrome spawns at configurationDone; the interval fires within a
        // beat of the page loading and the stop arrives flat via the child.
        // This ONE await covers the whole browser cold start (launch, page
        // load, breakpoint bind — ~2s when green), so it gets double the
        // default window: a loaded ubuntu runner burned the full 60s once
        // (actions/runs/29126426623 attempt 1) and the old frame-discarding
        // await left no evidence of why. If this trips again, the transcript
        // + adapter log in the failure message name the culprit.
        JSONObject stopped = nb.awaitEvent("stopped", Duration.ofSeconds(120));
        assertThat(stopped.getJSONObject("body").getString("reason"))
                .isEqualTo("breakpoint");
        int threadId = stopped.getJSONObject("body").getInt("threadId");

        nb.request("stackTrace", new JSONObject().put("threadId", threadId));
        JSONObject stack = nb.awaitResponse("stackTrace");
        JSONObject topFrame = stack.getJSONObject("body")
                .getJSONArray("stackFrames").getJSONObject(0);
        assertThat(topFrame.getInt("line")).isEqualTo(3);
        assertThat(topFrame.getJSONObject("source").getString("path"))
                .as("webRoot maps the browser's URL back to the real file")
                .isEqualTo(appJs.toString());

        // the launched browser is a live descendant of the adapter process
        List<ProcessHandle> tree = server.processHandle().descendants().toList();
        assertThat(tree).as("Chrome runs under the adapter").isNotEmpty();

        nb.request("continue", new JSONObject().put("threadId", threadId));
        nb.awaitResponse("continue");

        nb.request("disconnect", new JSONObject());

        // macOS + Linux: js-debug's cleanUp:wholeBrowser reaps the browser on
        // disconnect ALONE (recon: zero Chrome procs 3s after disconnect) —
        // the stronger property, pinned only where it holds. NOT on Windows:
        // js-debug renames the browser process, snapping the parent-PID chain
        // its forceful cleanup walks (same root cause documented on
        // ProcessSupport.killTree / ledger 38), so Chrome's detached tree
        // outlives disconnect. The product never relies on it there.
        if (!BaseUtilities.isWindows()) {
            assertTreeDeadWithin(tree, Duration.ofSeconds(30),
                    "disconnect tears down every browser process (macOS/Linux)");
        }

        // The contract the product actually guarantees on EVERY OS: Stop
        // (JsDebugServer.stop() -> ProcessSupport.killTreeAndWait — exactly
        // what BrowserDebugAction's session cleanup invokes) leaves zero
        // browser orphans. On Windows this is the ONLY reliable browser
        // reaper (ledger 40).
        server.stop();
        assertThat(tree.stream().noneMatch(ProcessHandle::isAlive))
                .as("Stop leaves zero browser orphans").isTrue();
    }

    /** Poll until every handle in {@code tree} has exited, or {@code budget}
     *  elapses; then assert the tree is dead. */
    private static void assertTreeDeadWithin(List<ProcessHandle> tree,
            Duration budget, String why) throws InterruptedException {
        long deadline = System.nanoTime() + budget.toNanos();
        while (tree.stream().anyMatch(ProcessHandle::isAlive)
                && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertThat(tree.stream().noneMatch(ProcessHandle::isAlive)).as(why).isTrue();
    }

    /** Loopback HTTP over the fixture dir — the in-JVM stand-in for the
     *  rack serve device the product would be pointed at. */
    private String serveFixture(Path dir) throws IOException {
        http = HttpServer.create(new InetSocketAddress(
                InetAddress.getLoopbackAddress(), 0), 0);
        http.createContext("/", exchange -> {
            String name = exchange.getRequestURI().getPath().substring(1);
            Path file = dir.resolve(name.isEmpty() ? "index.html" : name).normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] body = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type",
                    file.toString().endsWith(".html") ? "text/html" : "text/javascript");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        http.start();
        return "http://127.0.0.1:" + http.getAddress().getPort() + "/";
    }

    private static boolean nodePresent() {
        try {
            return ProcessSupport.runBounded(
                    List.of("node", "--version"), null,
                    java.time.Duration.ofSeconds(10)).ok();
        } catch (IOException ex) {
            return false;
        }
    }

    /** Minimal DAP client — started as a copy of RealJsDebugIntegrationTest's,
     *  then grew post-mortem diagnostics after a CI flake
     *  (actions/runs/29126426623): every frame lands in a timestamped
     *  transcript, EOF fails the pending await immediately instead of
     *  spinning out the window, and timeout messages carry the transcript
     *  plus the adapter's own output. Extract a shared helper when a third
     *  integration test appears. */
    private static final class Client {
        private static final Duration DEFAULT_AWAIT = Duration.ofSeconds(60);

        private final OutputStream out;
        private final BlockingQueue<JSONObject> frames = new LinkedBlockingQueue<>();
        private final AtomicInteger seq = new AtomicInteger();
        private final List<String> transcript = Collections.synchronizedList(new ArrayList<>());
        private final java.util.function.Supplier<String> extraDiagnostics;
        private final long epoch = System.nanoTime();
        private volatile boolean eof;

        Client(InputStream in, OutputStream out,
                java.util.function.Supplier<String> extraDiagnostics) {
            this.out = out;
            this.extraDiagnostics = extraDiagnostics;
            Thread reader = new Thread(() -> {
                try {
                    String json;
                    while ((json = DapFrames.read(in)) != null) {
                        JSONObject f = new JSONObject(json);
                        transcript.add(stamp() + " " + json);
                        frames.add(f);
                    }
                    transcript.add(stamp() + " <EOF>");
                } catch (IOException ex) {
                    // stream closed at teardown — or mid-session, which the
                    // pending await reports via the eof flag below
                    transcript.add(stamp() + " <stream error: " + ex + ">");
                } finally {
                    eof = true;
                }
            }, "test-nb-client");
            reader.setDaemon(true);
            reader.start();
        }

        void request(String command, JSONObject arguments) throws IOException {
            synchronized (out) {
                DapFrames.write(out, new JSONObject()
                        .put("seq", seq.incrementAndGet()).put("type", "request")
                        .put("command", command).put("arguments", arguments).toString());
            }
        }

        JSONObject awaitResponse(String command) throws InterruptedException {
            return await("response to '" + command + "'", DEFAULT_AWAIT,
                    f -> "response".equals(f.optString("type"))
                    && command.equals(f.optString("command")));
        }

        JSONObject awaitEvent(String event) throws InterruptedException {
            return awaitEvent(event, DEFAULT_AWAIT);
        }

        JSONObject awaitEvent(String event, Duration budget) throws InterruptedException {
            return await("event '" + event + "'", budget,
                    f -> "event".equals(f.optString("type"))
                    && event.equals(f.optString("event")));
        }

        private JSONObject await(String what, Duration budget,
                java.util.function.Predicate<JSONObject> match)
                throws InterruptedException {
            long deadline = System.nanoTime() + budget.toNanos();
            while (System.nanoTime() < deadline) {
                JSONObject f = frames.poll(250, TimeUnit.MILLISECONDS);
                if (f != null && match.test(f)) {
                    return f;
                }
                if (f == null && eof && frames.isEmpty()) {
                    // the session died under us — say so NOW, with the
                    // evidence, instead of burning the rest of the window
                    throw new AssertionError(
                            "DAP stream ended before " + what + " arrived\n" + diagnostics());
                }
            }
            throw new AssertionError(
                    what + " never arrived within " + budget.toSeconds() + "s\n" + diagnostics());
        }

        private String diagnostics() {
            return "--- DAP transcript (client view) ---\n" + renderTranscript()
                    + "\n--- js-debug adapter output ---\n" + extraDiagnostics.get();
        }

        /** Full transcript unless a runaway page flooded it (240 output
         *  events/minute from the fixture's interval): keep both ends —
         *  the launch story and what it was doing when time ran out. */
        private String renderTranscript() {
            List<String> lines = List.copyOf(transcript);
            if (lines.size() <= 120) {
                return String.join("\n", lines);
            }
            return String.join("\n", lines.subList(0, 60))
                    + "\n... [" + (lines.size() - 120) + " frames elided] ...\n"
                    + String.join("\n", lines.subList(lines.size() - 60, lines.size()));
        }

        private String stamp() {
            return String.format("%8.3fs", (System.nanoTime() - epoch) / 1e9);
        }
    }

    /**
     * Collects the adapter's own output for failure messages. JsDebugServer
     * already pumps every process line to its logger at FINE ("js-debug:
     * {0}") — this taps that logger rather than re-plumbing the process.
     * Chrome launch failures are only visible here: js-debug reports them on
     * stderr, not as DAP frames the client transcript would catch.
     */
    private static final class AdapterLogTap extends Handler implements AutoCloseable {
        private final Logger logger = Logger.getLogger(JsDebugServer.class.getName());
        private final Level oldLevel = logger.getLevel();
        private final List<String> lines = Collections.synchronizedList(new ArrayList<>());

        AdapterLogTap() {
            setLevel(Level.ALL);
            logger.setLevel(Level.FINE);
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            String msg = record.getMessage();
            if (msg != null && record.getParameters() != null) {
                msg = MessageFormat.format(msg, record.getParameters());
            }
            lines.add(msg);
        }

        String dump() {
            synchronized (lines) {
                return lines.isEmpty() ? "<no adapter output captured>"
                        : String.join("\n", lines);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
            logger.setLevel(oldLevel);
        }
    }
}

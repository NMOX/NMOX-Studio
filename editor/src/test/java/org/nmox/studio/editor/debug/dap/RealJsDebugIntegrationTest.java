package org.nmox.studio.editor.debug.dap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.nmox.studio.core.process.ProcessSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The recon transcript as a permanent regression test: the REAL vendored
 * js-debug adapter, a real node debuggee, and this test playing the
 * NetBeans client through the proxy. If js-debug's multi-session protocol
 * shifts under us on a version bump, this is the test that says so.
 */
@Timeout(120)
class RealJsDebugIntegrationTest {

    /** The vendored adapter, straight from the module's release dir. */
    private static final File SERVER_JS = new File(
            "src/main/release/jsdebug/js-debug/src/dapDebugServer.js").getAbsoluteFile();

    private JsDebugServer server;
    private DapProxy proxy;

    @AfterEach
    void tearDown() {
        if (proxy != null) {
            proxy.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("breakpoint in a node script: verified, hit, stack visible, continues to exit")
    void shouldHitBreakpointEndToEnd(@TempDir Path dir) throws Exception {
        assumeTrue(nodePresent(), "node not installed");
        assertThat(SERVER_JS).as("vendored adapter present").exists();

        Path hello = dir.resolve("hello.js");
        Files.writeString(hello, """
                const greeting = 'hello';
                const target = 'world';
                console.log(greeting + ' ' + target);
                console.log('done');
                """, StandardCharsets.UTF_8);

        server = JsDebugServer.start(SERVER_JS);
        proxy = DapProxy.start(server.port(), () -> { });
        Client nb = new Client(proxy.clientInput(), proxy.clientOutput());

        nb.request("initialize", new JSONObject()
                .put("clientID", "test").put("adapterID", "test")
                .put("pathFormat", "path")
                .put("linesStartAt1", true).put("columnsStartAt1", true));
        nb.awaitResponse("initialize");

        nb.request("launch", new JSONObject()
                .put("type", "pwa-node").put("request", "launch")
                .put("name", "hello.js")
                .put("program", hello.toAbsolutePath().toString())
                // NOT dir: whatever js-debug spawns inherits this cwd, and on
                // Windows a process's cwd handle blocks the directory's
                // deletion — @TempDir cleanup lost that race twice on CI
                // (files inside deleted fine, the ROOT stayed locked). A
                // durable cwd keeps the temp dir deletable no matter what
                // outlives the session by a beat.
                .put("cwd", SERVER_JS.getParentFile().getAbsolutePath())
                .put("console", "internalConsole")
                .put("autoAttachChildProcesses", false));

        nb.awaitEvent("initialized");
        nb.request("setBreakpoints", new JSONObject()
                .put("source", new JSONObject()
                        .put("path", hello.toAbsolutePath().toString()))
                .put("breakpoints", new JSONArray()
                        .put(new JSONObject().put("line", 3))));
        nb.awaitResponse("setBreakpoints");
        nb.request("configurationDone", new JSONObject());

        // the child session spins up behind the proxy; the stop arrives flat
        JSONObject stopped = nb.awaitEvent("stopped");
        assertThat(stopped.getJSONObject("body").getString("reason"))
                .isEqualTo("breakpoint");
        int threadId = stopped.getJSONObject("body").getInt("threadId");

        nb.request("stackTrace", new JSONObject().put("threadId", threadId));
        JSONObject stack = nb.awaitResponse("stackTrace");
        JSONObject topFrame = stack.getJSONObject("body")
                .getJSONArray("stackFrames").getJSONObject(0);
        assertThat(topFrame.getInt("line")).isEqualTo(3);

        nb.request("continue", new JSONObject().put("threadId", threadId));
        nb.awaitEvent("terminated");
    }

    @Test
    @DisplayName("a forked child process and a worker thread each get their own session and hit their breakpoints")
    void shouldDebugChildProcessesAndWorkersAsSessions(@TempDir Path tmp) throws Exception {
        assumeTrue(nodePresent(), "node not installed");
        assertThat(SERVER_JS).as("vendored adapter present").exists();
        // toRealPath: macOS hands @TempDir a /var symlink but js-debug reports
        // canonical /private/var paths back — compare like for like
        Path dir = tmp.toRealPath();

        // the ledger-25 shape: the program forks a child, then starts a worker
        Path parent = dir.resolve("parent.js");
        Path child = dir.resolve("child.js");
        Path worker = dir.resolve("worker.js");
        Files.writeString(parent, """
                const { fork } = require('child_process');
                const { Worker } = require('worker_threads');
                const c = fork(__dirname + '/child.js');
                c.on('exit', () => {
                  const w = new Worker(__dirname + '/worker.js');
                  w.on('exit', () => console.log('all done'));
                });
                """, StandardCharsets.UTF_8);
        Files.writeString(child, """
                const x = 1;
                console.log('child', x);
                """, StandardCharsets.UTF_8);
        Files.writeString(worker, """
                const y = 2;
                console.log('worker', y);
                """, StandardCharsets.UTF_8);

        server = JsDebugServer.start(SERVER_JS);
        proxy = DapProxy.start(server.port(), () -> { });
        // every session's breakpoint wishes — the platform sets the SAME
        // breakpoints on every session from its one Breakpoints window
        List<Path> breakpointFiles = List.of(parent, child, worker);
        Map<Path, Integer> breakpointLines = Map.of(parent, 3, child, 2, worker, 2);
        BlockingQueue<String> hits = new LinkedBlockingQueue<>();

        Client nb = new Client(proxy.clientInput(), proxy.clientOutput());
        nb.request("initialize", new JSONObject()
                .put("clientID", "test").put("adapterID", "test")
                .put("pathFormat", "path")
                .put("linesStartAt1", true).put("columnsStartAt1", true));
        nb.awaitResponse("initialize");
        nb.request("launch", new JSONObject()
                .put("type", "pwa-node").put("request", "launch")
                .put("name", "parent.js")
                .put("program", parent.toAbsolutePath().toString())
                .put("cwd", SERVER_JS.getParentFile().getAbsolutePath())
                .put("console", "internalConsole"));
        nb.awaitEvent("initialized");
        for (Path f : breakpointFiles) {
            nb.request("setBreakpoints", breakpoints(f, breakpointLines.get(f)));
            nb.awaitResponse("setBreakpoints");
        }
        nb.request("configurationDone", new JSONObject());

        // the flat session stops in parent.js like before …
        JSONObject stopped = nb.awaitEvent("stopped");
        int threadId = stopped.getJSONObject("body").getInt("threadId");
        nb.request("stackTrace", new JSONObject().put("threadId", threadId));
        JSONObject top = nb.awaitResponse("stackTrace").getJSONObject("body")
                .getJSONArray("stackFrames").getJSONObject(0);
        assertThat(top.getJSONObject("source").getString("path")).isEqualTo(parent.toAbsolutePath().toString());
        nb.request("continue", new JSONObject().put("threadId", threadId));

        // … then the fork asks for a second session, the way the platform
        // answers attachedChildSession: dial the port, initialize, bare attach
        JSONObject offer = nb.awaitRequest("attachedChildSession");
        nb.answer(offer);
        String childName = offer.getJSONObject("arguments").getJSONObject("config").getString("name");
        assertThat(childName).contains("child.js");
        new PlatformChildSession(offeredPort(offer), breakpointFiles, breakpointLines, hits);
        assertThat(hits.poll(60, TimeUnit.SECONDS)).as("the child's breakpoint hit in its own session")
                .isEqualTo(child.toAbsolutePath() + ":2");

        // the worker is started by parent.js (after the child exits), so
        // js-debug raises it on the PROGRAM's link — the flat root session —
        // not on the child's; the grandchild shape is pinned in DapProxyTest
        JSONObject workerOffer = nb.awaitRequest("attachedChildSession");
        nb.answer(workerOffer);
        assertThat(workerOffer.getJSONObject("arguments").getJSONObject("config").getString("name")).contains("worker");
        new PlatformChildSession(offeredPort(workerOffer), breakpointFiles, breakpointLines, hits);
        assertThat(hits.poll(60, TimeUnit.SECONDS)).as("the worker's breakpoint hit in its own session")
                .isEqualTo(worker.toAbsolutePath() + ":2");

        nb.awaitEvent("terminated");
    }

    private static JSONObject breakpoints(Path file, int line) {
        return new JSONObject()
                .put("source", new JSONObject().put("path", file.toAbsolutePath().toString()))
                .put("breakpoints", new JSONArray().put(new JSONObject().put("line", line)));
    }

    private static int offeredPort(JSONObject offer) {
        return Integer.parseInt(offer.getJSONObject("arguments").getJSONObject("config")
                .getString("__jsDebugChildServer"));
    }

    /**
     * What {@code DAPDebugger.attachedChildSession} does, sans UI: dial the
     * offered port, initialize, send a bare attach, set the shared
     * breakpoints on initialized, configurationDone, and on every stop read
     * the top frame then continue. Reports each hit as {@code path:line}.
     */
    private static final class PlatformChildSession {
        private final Client client;

        PlatformChildSession(int port, List<Path> files, Map<Path, Integer> lines,
                BlockingQueue<String> hits) throws IOException {
            java.net.Socket socket = new java.net.Socket(java.net.InetAddress.getLoopbackAddress(), port);
            client = new Client(socket.getInputStream(), socket.getOutputStream());
            Thread driver = new Thread(() -> {
                try {
                    client.request("initialize", new JSONObject().put("clientID", "nb").put("adapterID", "nb")
                            .put("pathFormat", "path").put("linesStartAt1", true).put("columnsStartAt1", true));
                    client.awaitResponse("initialize");
                    client.request("attach", null);
                    client.awaitEvent("initialized");
                    for (Path f : files) {
                        client.request("setBreakpoints", breakpoints(f, lines.get(f)));
                        client.awaitResponse("setBreakpoints");
                    }
                    client.request("configurationDone", new JSONObject());
                    JSONObject stopped = client.awaitEvent("stopped");
                    int thread = stopped.getJSONObject("body").getInt("threadId");
                    client.request("stackTrace", new JSONObject().put("threadId", thread));
                    JSONObject top = client.awaitResponse("stackTrace").getJSONObject("body")
                            .getJSONArray("stackFrames").getJSONObject(0);
                    hits.add(top.getJSONObject("source").getString("path") + ":" + top.getInt("line"));
                    client.request("continue", new JSONObject().put("threadId", thread));
                } catch (IOException | InterruptedException | RuntimeException | AssertionError ex) {
                    hits.add("FAILED: " + ex);
                }
            }, "test-platform-child-session");
            driver.setDaemon(true);
            driver.start();
        }

        JSONObject awaitRequest(String command) throws InterruptedException {
            return client.awaitRequest(command);
        }

        void answer(JSONObject request) throws IOException {
            client.answer(request);
        }
    }

    private static boolean nodePresent() {
        try {
            return ProcessSupport.runBounded(
                    java.util.List.of("node", "--version"), null,
                    java.time.Duration.ofSeconds(10)).ok();
        } catch (IOException ex) {
            return false;
        }
    }

    /** Minimal DAP client — what DAPConfiguration does, sans UI. */
    private static final class Client {
        private final OutputStream out;
        private final BlockingQueue<JSONObject> frames = new LinkedBlockingQueue<>();
        private final AtomicInteger seq = new AtomicInteger();

        Client(InputStream in, OutputStream out) {
            this.out = out;
            Thread reader = new Thread(() -> {
                try {
                    String json;
                    while ((json = DapFrames.read(in)) != null) {
                        frames.add(new JSONObject(json));
                    }
                } catch (IOException ignored) {
                    // stream closed at teardown
                }
            }, "test-nb-client");
            reader.setDaemon(true);
            reader.start();
        }

        void request(String command, JSONObject arguments) throws IOException {
            synchronized (out) {
                JSONObject frame = new JSONObject()
                        .put("seq", seq.incrementAndGet()).put("type", "request")
                        .put("command", command);
                if (arguments != null) {
                    frame.put("arguments", arguments);
                }
                DapFrames.write(out, frame.toString());
            }
        }

        /** The platform's success reply to a reverse request (attachedChildSession). */
        void answer(JSONObject request) throws IOException {
            synchronized (out) {
                DapFrames.write(out, new JSONObject()
                        .put("seq", seq.incrementAndGet()).put("type", "response")
                        .put("command", request.optString("command"))
                        .put("request_seq", request.optInt("seq"))
                        .put("success", true).put("body", new JSONObject()).toString());
            }
        }

        JSONObject awaitRequest(String command) throws InterruptedException {
            return await(f -> "request".equals(f.optString("type"))
                    && command.equals(f.optString("command")));
        }

        JSONObject awaitResponse(String command) throws InterruptedException {
            return await(f -> "response".equals(f.optString("type"))
                    && command.equals(f.optString("command")));
        }

        JSONObject awaitEvent(String event) throws InterruptedException {
            return await(f -> "event".equals(f.optString("type"))
                    && event.equals(f.optString("event")));
        }

        private JSONObject await(java.util.function.Predicate<JSONObject> match)
                throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (System.nanoTime() < deadline) {
                JSONObject f = frames.poll(250, TimeUnit.MILLISECONDS);
                if (f != null && match.test(f)) {
                    return f;
                }
            }
            throw new AssertionError("expected frame never arrived");
        }
    }
}

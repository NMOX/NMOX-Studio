package org.nmox.studio.editor.debug.dap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
                DapFrames.write(out, new JSONObject()
                        .put("seq", seq.incrementAndGet()).put("type", "request")
                        .put("command", command).put("arguments", arguments).toString());
            }
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

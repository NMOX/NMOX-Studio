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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
 * through webRoot to the real file; and a client disconnect alone tears
 * down every browser process (js-debug's cleanUp: wholeBrowser default) —
 * the platform's Stop needs no extra kill for Chrome.
 */
@Timeout(180)
class RealChromeIntegrationTest {

    /** The vendored adapter, straight from the module's release dir. */
    private static final File SERVER_JS = new File(
            "src/main/release/jsdebug/js-debug/src/dapDebugServer.js").getAbsoluteFile();

    private HttpServer http;
    private JsDebugServer server;
    private DapProxy proxy;
    private Path profile;

    @AfterEach
    void tearDown() throws IOException {
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
    @DisplayName("breakpoint in browser-side JS: hit in headless Chrome, stack mapped to the file, disconnect kills the browser")
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

        server = JsDebugServer.start(SERVER_JS);
        proxy = DapProxy.start(server.port(), () -> { });
        Client nb = new Client(proxy.clientInput(), proxy.clientOutput());

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
        // beat of the page loading and the stop arrives flat via the child
        JSONObject stopped = nb.awaitEvent("stopped");
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

        // Stop semantics: a client disconnect ALONE must take Chrome down —
        // js-debug owns the browser it launched (cleanUp: wholeBrowser)
        nb.request("disconnect", new JSONObject());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (tree.stream().anyMatch(ProcessHandle::isAlive)
                && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertThat(tree.stream().noneMatch(ProcessHandle::isAlive))
                .as("disconnect tears down every browser process").isTrue();
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

    /** Minimal DAP client — copied from RealJsDebugIntegrationTest; extract
     *  a shared helper when a third integration test appears. */
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

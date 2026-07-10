package org.nmox.studio.editor.debug.dap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the proxy against a scripted fake adapter that replays the shape
 * observed live against the real dapDebugServer (the recon transcript):
 * a coordinator parent session plus a child session requested through the
 * startDebugging reverse request.
 */
@Timeout(30)
class DapProxyTest {

    private FakeAdapter adapter;
    private DapProxy proxy;
    private DapClient client;
    private final CountDownLatch closedCallback = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws IOException {
        adapter = new FakeAdapter();
        proxy = DapProxy.start(adapter.port(), closedCallback::countDown);
        client = new DapClient(proxy.clientInput(), proxy.clientOutput());
    }

    @AfterEach
    void tearDown() {
        proxy.close();
        adapter.close();
    }

    @Test
    @DisplayName("phase 1 is pure pass-through: requests reach the parent, responses map back")
    void shouldPassThroughBeforeChild() throws Exception {
        client.request("initialize", new JSONObject().put("clientID", "test"));
        JSONObject seen = adapter.parentReceived();
        assertThat(seen.getString("command")).isEqualTo("initialize");

        adapter.respondParent(seen, new JSONObject().put("supportsConfigurationDoneRequest", true));
        JSONObject response = client.awaitResponse("initialize");
        assertThat(response.getInt("request_seq")).isEqualTo(client.seqOf("initialize"));
        assertThat(response.getBoolean("success")).isTrue();
    }

    @Test
    @DisplayName("startDebugging is answered by the proxy and never reaches the client")
    void shouldAnswerStartDebugging() throws Exception {
        driveToChildDance();
        JSONObject reply = adapter.parentReceived();
        assertThat(reply.getString("type")).isEqualTo("response");
        assertThat(reply.getString("command")).isEqualTo("startDebugging");
        assertThat(reply.getBoolean("success")).isTrue();
        assertThat(client.receivedCommands()).doesNotContain("startDebugging");
    }

    @Test
    @DisplayName("the child dance: initialize, launch with the configuration verbatim, breakpoint replay, configurationDone")
    void shouldRunChildDance() throws Exception {
        client.request("setBreakpoints", new JSONObject()
                .put("source", new JSONObject().put("path", "/tmp/hello.js"))
                .put("breakpoints", new JSONArray().put(new JSONObject().put("line", 3))));
        adapter.respondParent(adapter.parentReceived(), new JSONObject());
        client.awaitResponse("setBreakpoints");

        driveToChildDance();
        adapter.parentReceived(); // proxy's success reply to startDebugging

        JSONObject childInit = adapter.childReceived();
        assertThat(childInit.getString("command")).isEqualTo("initialize");
        adapter.respondChild(childInit, new JSONObject());

        JSONObject childLaunch = adapter.childReceived();
        assertThat(childLaunch.getString("command")).isEqualTo("launch");
        assertThat(childLaunch.getJSONObject("arguments").getString("__pendingTargetId"))
                .isEqualTo("target-1");

        adapter.eventChild("initialized", new JSONObject());
        JSONObject replayed = adapter.childReceived();
        assertThat(replayed.getString("command")).isEqualTo("setBreakpoints");
        assertThat(replayed.getJSONObject("arguments").getJSONObject("source")
                .getString("path")).isEqualTo("/tmp/hello.js");
        assertThat(adapter.childReceived().getString("command"))
                .isEqualTo("configurationDone");
    }

    @Test
    @DisplayName("after the splice, client requests route to the child and its events flow up")
    void shouldSpliceChildIntoSession() throws Exception {
        spliceChild();

        client.request("threads", new JSONObject());
        JSONObject onChild = adapter.childReceived();
        assertThat(onChild.getString("command")).isEqualTo("threads");
        adapter.respondChild(onChild, new JSONObject()
                .put("threads", new JSONArray().put(new JSONObject()
                        .put("id", 1).put("name", "main"))));
        JSONObject response = client.awaitResponse("threads");
        assertThat(response.getInt("request_seq")).isEqualTo(client.seqOf("threads"));

        adapter.eventChild("stopped", new JSONObject()
                .put("reason", "breakpoint").put("threadId", 1));
        JSONObject stopped = client.awaitEvent("stopped");
        assertThat(stopped.getJSONObject("body").getString("reason"))
                .isEqualTo("breakpoint");
    }

    @Test
    @DisplayName("disconnect fans out to both sessions; teardown fires the callback once")
    void shouldFanOutDisconnect() throws Exception {
        spliceChild();

        client.request("disconnect", new JSONObject());
        assertThat(adapter.childReceived().getString("command")).isEqualTo("disconnect");
        assertThat(adapter.parentReceived().getString("command")).isEqualTo("disconnect");
        adapter.close();
        assertThat(closedCallback.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("a disconnect racing the child dance still reaches the child")
    void shouldFanOutDisconnectDuringChildDance() throws Exception {
        // The Windows-lane catch: the splice flag flips only after the
        // initialized/replay handshake, but the child CONNECTION exists from
        // the startDebugging answer on. A disconnect landing in that window
        // used to go parent-only — and a child that never hears disconnect
        // keeps the debuggee alive. Freeze the dance mid-flight (initialize
        // answered, launch consumed, no initialized event yet) and insist
        // the child hears the disconnect anyway.
        driveToChildDance();
        adapter.parentReceived(); // success reply to startDebugging
        adapter.respondChild(adapter.childReceived(), new JSONObject()); // initialize
        adapter.childReceived(); // launch — the dance now waits on initialized

        client.request("disconnect", new JSONObject());
        assertThat(adapter.childReceived().getString("command")).isEqualTo("disconnect");
        assertThat(adapter.parentReceived().getString("command")).isEqualTo("disconnect");
    }

    @Test
    @DisplayName("a second startDebugging gets a polite yes and no third connection")
    void shouldIgnoreExtraTargets() throws Exception {
        spliceChild();

        adapter.requestParent("startDebugging", new JSONObject()
                .put("request", "launch")
                .put("configuration", new JSONObject().put("__pendingTargetId", "target-2")));
        JSONObject reply = adapter.parentReceived();
        assertThat(reply.getBoolean("success")).isTrue();
        assertThat(adapter.connectionCount()).isEqualTo(2);
    }

    // --- scripted flows ---------------------------------------------------

    private void driveToChildDance() throws Exception {
        client.request("launch", new JSONObject().put("program", "/tmp/hello.js"));
        adapter.respondParent(adapter.parentReceived(), new JSONObject());
        client.awaitResponse("launch");
        adapter.requestParent("startDebugging", new JSONObject()
                .put("request", "launch")
                .put("configuration", new JSONObject()
                        .put("type", "pwa-node")
                        .put("__pendingTargetId", "target-1")));
    }

    @Test
    @DisplayName("terminated reaches the client, then a clean EOF — its socket is never slammed shut")
    void shouldHalfCloseClientAfterTerminated() throws Exception {
        spliceChild();
        adapter.eventChild("terminated", new JSONObject());

        assertThat(client.awaitEvent("terminated"))
                .as("the last event of the session must be delivered").isNotNull();
        assertThat(closedCallback.await(10, TimeUnit.SECONDS))
                .as("the adapter is stopped once the session ends").isTrue();

        // The proxy half-closes its end so the FIN queues *behind* the frames
        // it already wrote. Closing the client's socket instead (the pre-fix
        // behaviour) discards whatever the client hasn't read yet — reliably
        // the terminated event on Linux — and getInputStream() throws here.
        assertThat(proxy.clientInput().read())
                .as("clean EOF, not a slammed socket").isEqualTo(-1);
    }

    private void spliceChild() throws Exception {
        driveToChildDance();
        adapter.parentReceived(); // success reply to startDebugging
        adapter.respondChild(adapter.childReceived(), new JSONObject()); // initialize
        adapter.childReceived(); // launch
        adapter.eventChild("initialized", new JSONObject());
        adapter.childReceived(); // configurationDone (no breakpoints cached here)
    }

    // --- test doubles -----------------------------------------------------

    /** The NetBeans side: writes requests, collects everything that returns. */
    private static final class DapClient {
        private final OutputStream out;
        private final BlockingQueue<JSONObject> responses = new LinkedBlockingQueue<>();
        private final BlockingQueue<JSONObject> events = new LinkedBlockingQueue<>();
        private final List<String> allCommands = new CopyOnWriteArrayList<>();
        private final java.util.Map<String, Integer> sentSeqs = new java.util.concurrent.ConcurrentHashMap<>();
        private final AtomicInteger seq = new AtomicInteger();

        DapClient(InputStream in, OutputStream out) {
            this.out = out;
            Thread reader = new Thread(() -> {
                try {
                    String json;
                    while ((json = DapFrames.read(in)) != null) {
                        JSONObject frame = new JSONObject(json);
                        allCommands.add(frame.optString("command", frame.optString("event")));
                        if ("response".equals(frame.optString("type"))) {
                            responses.add(frame);
                        } else if ("event".equals(frame.optString("type"))) {
                            events.add(frame);
                        }
                    }
                } catch (IOException ignored) {
                    // stream closed at teardown
                }
            }, "test-dap-client");
            reader.setDaemon(true);
            reader.start();
        }

        void request(String command, JSONObject arguments) throws IOException {
            int s = seq.incrementAndGet();
            sentSeqs.put(command, s);
            synchronized (out) {
                DapFrames.write(out, new JSONObject()
                        .put("seq", s).put("type", "request")
                        .put("command", command).put("arguments", arguments).toString());
            }
        }

        int seqOf(String command) {
            return sentSeqs.get(command);
        }

        JSONObject awaitResponse(String command) throws InterruptedException {
            return await(responses, f -> command.equals(f.optString("command")));
        }

        JSONObject awaitEvent(String event) throws InterruptedException {
            return await(events, f -> event.equals(f.optString("event")));
        }

        List<String> receivedCommands() {
            return allCommands;
        }

        private static JSONObject await(BlockingQueue<JSONObject> queue,
                java.util.function.Predicate<JSONObject> match) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                JSONObject f = queue.poll(200, TimeUnit.MILLISECONDS);
                if (f != null && match.test(f)) {
                    return f;
                }
            }
            throw new AssertionError("expected frame never arrived");
        }
    }

    /** The js-debug side: accepts parent then child, scripted by the test. */
    private static final class FakeAdapter {
        private final ServerSocket server;
        private final List<Socket> connections = new CopyOnWriteArrayList<>();
        private final BlockingQueue<JSONObject> parentIn = new LinkedBlockingQueue<>();
        private final BlockingQueue<JSONObject> childIn = new LinkedBlockingQueue<>();
        private final AtomicInteger adapterSeq = new AtomicInteger(1000);

        FakeAdapter() throws IOException {
            server = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(() -> {
                try {
                    while (!server.isClosed()) {
                        Socket s = server.accept();
                        connections.add(s);
                        BlockingQueue<JSONObject> sink =
                                connections.size() == 1 ? parentIn : childIn;
                        Thread reader = new Thread(() -> {
                            try {
                                String json;
                                while ((json = DapFrames.read(s.getInputStream())) != null) {
                                    sink.add(new JSONObject(json));
                                }
                            } catch (IOException ignored) {
                                // connection closed
                            }
                        }, "fake-adapter-reader-" + connections.size());
                        reader.setDaemon(true);
                        reader.start();
                    }
                } catch (IOException ignored) {
                    // server closed
                }
            }, "fake-adapter-acceptor");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() {
            return server.getLocalPort();
        }

        int connectionCount() {
            return connections.size();
        }

        JSONObject parentReceived() throws InterruptedException {
            JSONObject f = parentIn.poll(10, TimeUnit.SECONDS);
            assertThat(f).as("frame expected on parent connection").isNotNull();
            return f;
        }

        JSONObject childReceived() throws InterruptedException {
            JSONObject f = childIn.poll(10, TimeUnit.SECONDS);
            assertThat(f).as("frame expected on child connection").isNotNull();
            return f;
        }

        void respondParent(JSONObject request, JSONObject body) throws IOException {
            sendTo(0, response(request, body));
        }

        void respondChild(JSONObject request, JSONObject body) throws IOException {
            sendTo(1, response(request, body));
        }

        void requestParent(String command, JSONObject arguments) throws IOException {
            sendTo(0, new JSONObject()
                    .put("seq", adapterSeq.incrementAndGet()).put("type", "request")
                    .put("command", command).put("arguments", arguments));
        }

        void eventChild(String event, JSONObject body) throws IOException {
            sendTo(1, new JSONObject()
                    .put("seq", adapterSeq.incrementAndGet()).put("type", "event")
                    .put("event", event).put("body", body));
        }

        private JSONObject response(JSONObject request, JSONObject body) {
            return new JSONObject()
                    .put("seq", adapterSeq.incrementAndGet()).put("type", "response")
                    .put("command", request.getString("command"))
                    .put("request_seq", request.getInt("seq"))
                    .put("success", true).put("body", body);
        }

        private void sendTo(int connection, JSONObject frame) throws IOException {
            Socket s = connections.get(connection);
            synchronized (s) {
                DapFrames.write(s.getOutputStream(), frame.toString());
            }
        }

        void close() {
            try {
                server.close();
            } catch (IOException ignored) {
                // teardown
            }
            connections.forEach(s -> {
                try {
                    s.close();
                } catch (IOException ignored) {
                    // teardown
                }
            });
        }
    }
}

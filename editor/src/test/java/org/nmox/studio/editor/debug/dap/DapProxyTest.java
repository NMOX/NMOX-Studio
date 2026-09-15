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
    @DisplayName("a second target is offered to the platform as its own session, on a port the proxy owns")
    void shouldOfferExtraTargetAsChildSession() throws Exception {
        spliceChild();

        adapter.requestParent("startDebugging", new JSONObject()
                .put("request", "launch")
                .put("configuration", new JSONObject()
                        .put("type", "pwa-node").put("name", "child.js [4242]")
                        .put("__pendingTargetId", "target-2")));
        JSONObject reply = adapter.parentReceived();
        assertThat(reply.getString("command")).isEqualTo("startDebugging");
        assertThat(reply.getBoolean("success")).isTrue();

        JSONObject offer = client.awaitRequest("attachedChildSession");
        JSONObject config = offer.getJSONObject("arguments").getJSONObject("config");
        assertThat(config.getString("name")).isEqualTo("child.js [4242]");
        int port = Integer.parseInt(config.getString("__jsDebugChildServer"));
        assertThat(port).isBetween(1, 65535);
        assertThat(adapter.connectionCount())
                .as("nothing is dialed until the platform dials the offered port")
                .isEqualTo(2);
        assertThat(proxy.childSessions()).isEqualTo(1);
    }

    @Test
    @DisplayName("the platform's bare attach on the offered port reaches the adapter as the target's launch")
    void shouldRelayChildSessionWithTheTargetsConfiguration() throws Exception {
        spliceChild();
        adapter.requestParent("startDebugging", new JSONObject()
                .put("request", "launch")
                .put("configuration", new JSONObject()
                        .put("type", "pwa-node").put("name", "child.js [4242]")
                        .put("__pendingTargetId", "target-2")));
        adapter.parentReceived(); // success reply
        int port = offeredPort(client.awaitRequest("attachedChildSession"));

        // the platform's side of attachedChildSession: dial, initialize, bare attach
        DapClient session = new DapClient(port);
        session.request("initialize", new JSONObject().put("clientID", "nb"));
        JSONObject init = adapter.received(3);
        assertThat(init.getString("command")).isEqualTo("initialize");
        adapter.respond(3, init, new JSONObject());
        assertThat(session.awaitResponse("initialize").getBoolean("success")).isTrue();

        session.request("attach", null);
        JSONObject launch = adapter.received(3);
        assertThat(launch.getString("command")).as("a bare attach becomes the target's launch").isEqualTo("launch");
        assertThat(launch.getJSONObject("arguments").getString("__pendingTargetId")).isEqualTo("target-2");
        adapter.respond(3, launch, new JSONObject());
        assertThat(session.awaitResponse("attach")).as("the answer comes back under the platform's own command")
                .isNotNull();

        // and the target's events flow to the platform's session, not the flat one
        adapter.event(3, "stopped", new JSONObject().put("reason", "breakpoint").put("threadId", 7));
        assertThat(session.awaitEvent("stopped").getJSONObject("body").getInt("threadId")).isEqualTo(7);
        assertThat(client.receivedCommands()).doesNotContain("stopped");
    }

    @Test
    @DisplayName("a startDebugging raised on a child session's link spawns a grandchild session on that link")
    void shouldRelayGrandchildOnTheChildLink() throws Exception {
        spliceChild();
        adapter.requestParent("startDebugging", new JSONObject()
                .put("request", "launch")
                .put("configuration", new JSONObject().put("type", "pwa-node")
                        .put("name", "child.js [1]").put("__pendingTargetId", "target-2")));
        adapter.parentReceived();
        DapClient session = new DapClient(offeredPort(client.awaitRequest("attachedChildSession")));
        session.request("initialize", new JSONObject());
        adapter.respond(3, adapter.received(3), new JSONObject());

        adapter.request(3, "startDebugging", new JSONObject()
                .put("request", "launch")
                .put("configuration", new JSONObject().put("type", "pwa-node")
                        .put("name", "[worker 1]").put("__pendingTargetId", "target-2-1")));
        JSONObject reply = adapter.received(3);
        assertThat(reply.getString("command")).isEqualTo("startDebugging");
        assertThat(reply.getBoolean("success")).isTrue();
        JSONObject offer = session.awaitRequest("attachedChildSession");
        assertThat(offer.getJSONObject("arguments").getJSONObject("config").getString("name"))
                .isEqualTo("[worker 1]");
        assertThat(client.receivedCommands().stream().filter("attachedChildSession"::equals).count())
                .as("the root heard ONE offer (the child); the grandchild is offered on ITS parent's link")
                .isEqualTo(1);
        assertThat(proxy.childSessions()).isEqualTo(2);
    }

    @Test
    @DisplayName("closing the proxy tears every child session's sockets down")
    void shouldCloseChildSessionsWithTheProxy() throws Exception {
        spliceChild();
        adapter.requestParent("startDebugging", new JSONObject()
                .put("request", "launch")
                .put("configuration", new JSONObject().put("type", "pwa-node")
                        .put("name", "child").put("__pendingTargetId", "t2")));
        adapter.parentReceived();
        DapClient session = new DapClient(offeredPort(client.awaitRequest("attachedChildSession")));
        session.request("initialize", new JSONObject());
        adapter.received(3);

        proxy.close();
        assertThat(session.awaitEof(5_000)).as("the platform's child session reads EOF").isTrue();
    }

    private static int offeredPort(JSONObject offer) {
        return Integer.parseInt(offer.getJSONObject("arguments").getJSONObject("config")
                .getString("__jsDebugChildServer"));
    }

    @Test
    @DisplayName("pwa-chrome: the browser child dance replays the recon transcript shape")
    void shouldRunChromeShapedChildDance() throws Exception {
        // the v1.43.0 recon transcript, verbatim shapes: a pwa-chrome launch,
        // breakpoints cached during configuration, then the page target's
        // startDebugging (name "about:blank", a __pendingTargetId) on the
        // parent link and the same configuration replayed to the child
        client.request("setBreakpoints", new JSONObject()
                .put("source", new JSONObject().put("path", "/work/site/app.js"))
                .put("breakpoints", new JSONArray().put(new JSONObject().put("line", 7))));
        adapter.respondParent(adapter.parentReceived(), new JSONObject());
        client.awaitResponse("setBreakpoints");

        client.request("launch", new JSONObject()
                .put("type", "pwa-chrome").put("request", "launch")
                .put("url", "http://127.0.0.1:3000/")
                .put("webRoot", "/work/site")
                .put("runtimeExecutable", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
                .put("userDataDir", "/tmp/profile-1"));
        adapter.respondParent(adapter.parentReceived(), new JSONObject());
        client.awaitResponse("launch");
        adapter.requestParent("startDebugging", new JSONObject()
                .put("request", "launch")
                .put("configuration", new JSONObject()
                        .put("type", "pwa-chrome")
                        .put("name", "about:blank")
                        .put("__pendingTargetId", "B4E5D9B9E3BCEC26C0954D2D1AF9DB47")));
        adapter.parentReceived(); // proxy's success reply

        adapter.respondChild(adapter.childReceived(), new JSONObject()); // initialize
        JSONObject childLaunch = adapter.childReceived();
        assertThat(childLaunch.getString("command")).isEqualTo("launch");
        assertThat(childLaunch.getJSONObject("arguments").getString("type"))
                .isEqualTo("pwa-chrome");
        assertThat(childLaunch.getJSONObject("arguments").getString("__pendingTargetId"))
                .isEqualTo("B4E5D9B9E3BCEC26C0954D2D1AF9DB47");

        adapter.eventChild("initialized", new JSONObject());
        JSONObject replayed = adapter.childReceived();
        assertThat(replayed.getString("command")).isEqualTo("setBreakpoints");
        assertThat(replayed.getJSONObject("arguments").getJSONObject("source")
                .getString("path")).isEqualTo("/work/site/app.js");
        assertThat(adapter.childReceived().getString("command"))
                .isEqualTo("configurationDone");
    }

    @Test
    @DisplayName("a worker target's startDebugging on the CHILD link is answered there and offered to the platform")
    void shouldOfferWorkerTargetFromChildLink() throws Exception {
        // recon finding: for browsers the page target's startDebugging comes
        // on the parent link but WORKER targets arrive on the CHILD link.
        // The proxy must answer where it was asked — an unanswered reverse
        // request wedges js-debug — and the worker gets its own session
        // (ledger 39: an answered-but-unattached worker sat paused forever).
        spliceChild();

        adapter.requestChild("startDebugging", new JSONObject()
                .put("request", "launch")
                .put("configuration", new JSONObject()
                        .put("type", "pwa-chrome")
                        .put("name", "http://127.0.0.1:3000/worker.js")
                        .put("__pendingTargetId", "worker-target-1")));

        JSONObject reply = adapter.childReceived();
        assertThat(reply.getString("type")).isEqualTo("response");
        assertThat(reply.getString("command")).isEqualTo("startDebugging");
        assertThat(reply.getBoolean("success")).isTrue();
        JSONObject offer = client.awaitRequest("attachedChildSession");
        assertThat(offer.getJSONObject("arguments").getJSONObject("config").getString("name"))
                .isEqualTo("http://127.0.0.1:3000/worker.js");
        assertThat(client.receivedCommands()).doesNotContain("startDebugging");
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

    @Test
    @DisplayName("once the client closes its socket, the loopback pair is reaped — no FD leak per session")
    void shouldReapClientPairAfterClientCloses() throws Exception {
        // ledger 55 M1: production never calls close() (the proxy is a local
        // in the debug actions), so endSession's half-close left BOTH pair
        // sockets open for the IDE's lifetime — one leaked pair per debug
        // session. The reap point is the client pump's clean EOF: the client
        // has closed, nothing unread can be discarded.
        spliceChild();
        adapter.eventChild("terminated", new JSONObject());
        client.awaitEvent("terminated");
        assertThat(proxy.clientInput().read())
                .as("half-close delivered first — the M1 fix must not regress it")
                .isEqualTo(-1);

        proxy.clientOutput().close();   // the platform client closing its socket

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!proxy.clientPairClosed()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("loopback pair never reaped after client close");
            }
            Thread.sleep(5);
        }
    }

    @Test
    @DisplayName("a malformed startDebugging is refused, not acked-then-dropped")
    void shouldRefuseMalformedStartDebugging() throws Exception {
        // ledger 55 L3: the proxy used to ack success FIRST, then throw
        // parsing the configuration — the parent believed a child launched
        // that never would. Parse-before-ack turns that into an honest no.
        client.request("launch", new JSONObject().put("program", "/tmp/hello.js"));
        adapter.respondParent(adapter.parentReceived(), new JSONObject());
        client.awaitResponse("launch");

        adapter.requestParent("startDebugging", new JSONObject()
                .put("request", "launch"));   // no "configuration" at all
        JSONObject reply = adapter.parentReceived();
        assertThat(reply.getString("command")).isEqualTo("startDebugging");
        assertThat(reply.getBoolean("success"))
                .as("malformed config = failure response, never a false yes")
                .isFalse();
        assertThat(adapter.connectionCount())
                .as("no child connection is dialed for a refused request")
                .isEqualTo(1);
    }

    private void spliceChild() throws Exception {
        driveToChildDance();
        adapter.parentReceived(); // success reply to startDebugging
        adapter.respondChild(adapter.childReceived(), new JSONObject()); // initialize
        adapter.childReceived(); // launch
        adapter.eventChild("initialized", new JSONObject());
        adapter.childReceived(); // configurationDone (no breakpoints cached here)
        // The proxy sets `spliced` on the line *after* it writes
        // configurationDone, so observing that frame on the child doesn't
        // prove the flag is up yet. Await the flag itself before firing the
        // first post-splice request — otherwise it can win the race and route
        // to the parent, and the child never sees it. (Windows CI, PR #128.)
        awaitSpliced();
    }

    private void awaitSpliced() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!proxy.spliced()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("proxy never reached the spliced state");
            }
            Thread.sleep(2);
        }
    }

    // --- test doubles -----------------------------------------------------

    /** The NetBeans side: writes requests, collects everything that returns. */
    private static final class DapClient {
        private final OutputStream out;
        private final BlockingQueue<JSONObject> responses = new LinkedBlockingQueue<>();
        private final BlockingQueue<JSONObject> events = new LinkedBlockingQueue<>();
        private final BlockingQueue<JSONObject> requests = new LinkedBlockingQueue<>();
        private final List<String> allCommands = new CopyOnWriteArrayList<>();
        private final java.util.Map<String, Integer> sentSeqs = new java.util.concurrent.ConcurrentHashMap<>();
        private final AtomicInteger seq = new AtomicInteger();
        private final CountDownLatch eof = new CountDownLatch(1);

        DapClient(InputStream in, OutputStream out) {
            this.out = out;
            Thread reader = new Thread(() -> {
                try {
                    String json;
                    while ((json = DapFrames.read(in)) != null) {
                        JSONObject frame = new JSONObject(json);
                        allCommands.add(frame.optString("command", frame.optString("event")));
                        switch (frame.optString("type")) {
                            case "response" -> responses.add(frame);
                            case "event" -> events.add(frame);
                            case "request" -> requests.add(frame);
                            default -> { }
                        }
                    }
                } catch (IOException ignored) {
                    // stream closed at teardown
                } finally {
                    eof.countDown();
                }
            }, "test-dap-client");
            reader.setDaemon(true);
            reader.start();
        }

        /** The platform's side of attachedChildSession: a fresh client on the offered port. */
        DapClient(int port) throws IOException {
            this(dialFor(port));
        }

        private DapClient(Socket socket) throws IOException {
            this(socket.getInputStream(), socket.getOutputStream());
        }

        private static Socket dialFor(int port) throws IOException {
            return new Socket(InetAddress.getLoopbackAddress(), port);
        }

        boolean awaitEof(long millis) throws InterruptedException {
            return eof.await(millis, TimeUnit.MILLISECONDS);
        }

        void request(String command, JSONObject arguments) throws IOException {
            int s = seq.incrementAndGet();
            sentSeqs.put(command, s);
            synchronized (out) {
                JSONObject frame = new JSONObject()
                        .put("seq", s).put("type", "request").put("command", command);
                if (arguments != null) {
                    frame.put("arguments", arguments);
                }
                DapFrames.write(out, frame.toString());
            }
        }

        JSONObject awaitRequest(String command) throws InterruptedException {
            return await(requests, f -> command.equals(f.optString("command")));
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

    /** The js-debug side: accepts parent, child, and every further session, scripted by the test. */
    private static final class FakeAdapter {
        private final ServerSocket server;
        private final List<Socket> connections = new CopyOnWriteArrayList<>();
        private final List<BlockingQueue<JSONObject>> inbox = new CopyOnWriteArrayList<>();
        private final AtomicInteger adapterSeq = new AtomicInteger(1000);

        FakeAdapter() throws IOException {
            server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(() -> {
                try {
                    while (!server.isClosed()) {
                        Socket s = server.accept();
                        BlockingQueue<JSONObject> sink = new LinkedBlockingQueue<>();
                        inbox.add(sink);
                        connections.add(s);
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
            return received(1);
        }

        JSONObject childReceived() throws InterruptedException {
            return received(2);
        }

        /** The next frame on the Nth connection (1 = parent, 2 = the spliced child, 3+ = child sessions). */
        JSONObject received(int connection) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (inbox.size() < connection && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertThat(inbox.size()).as("connection " + connection + " exists").isGreaterThanOrEqualTo(connection);
            JSONObject f = inbox.get(connection - 1).poll(10, TimeUnit.SECONDS);
            assertThat(f).as("frame expected on connection " + connection).isNotNull();
            return f;
        }

        void respondParent(JSONObject request, JSONObject body) throws IOException {
            respond(1, request, body);
        }

        void respondChild(JSONObject request, JSONObject body) throws IOException {
            respond(2, request, body);
        }

        void respond(int connection, JSONObject request, JSONObject body) throws IOException {
            sendTo(connection - 1, response(request, body));
        }

        void requestParent(String command, JSONObject arguments) throws IOException {
            request(1, command, arguments);
        }

        /** Browser worker targets ask on the CHILD connection (recon-pinned). */
        void requestChild(String command, JSONObject arguments) throws IOException {
            request(2, command, arguments);
        }

        void request(int connection, String command, JSONObject arguments) throws IOException {
            sendTo(connection - 1, new JSONObject()
                    .put("seq", adapterSeq.incrementAndGet()).put("type", "request")
                    .put("command", command).put("arguments", arguments));
        }

        void eventChild(String event, JSONObject body) throws IOException {
            event(2, event, body);
        }

        void event(int connection, String event, JSONObject body) throws IOException {
            sendTo(connection - 1, new JSONObject()
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

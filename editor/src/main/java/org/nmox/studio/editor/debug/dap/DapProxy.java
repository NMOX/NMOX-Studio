package org.nmox.studio.editor.debug.dap;

import org.nmox.studio.core.util.Threads;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;

/**
 * Flattens js-debug's multi-session protocol into the single DAP session
 * the NetBeans debugger client speaks.
 *
 * js-debug's first connection is only a coordinator: after {@code launch}
 * it sends a {@code startDebugging} reverse request and expects the client
 * to open a SECOND connection for the real target — the platform client
 * (stream-based, no ability to dial) can never do that, so the debuggee
 * sits paused forever. This proxy sits between them: pass-through until
 * {@code startDebugging}, then it answers the reverse request itself,
 * dials the child, replays the client's breakpoints, and from then on
 * routes client requests to the child and both connections' events back
 * up — one flat session as far as NetBeans can tell.
 *
 * <p>Every FURTHER target — a child process the program forks, a worker
 * thread, a page's Web Worker — arrives as another {@code startDebugging},
 * on whichever link spawned it. Those become real NetBeans sessions of
 * their own (v2.156.0): the platform's DAP client cannot dial a socket for
 * a {@code startDebugging}, but it does implement js-debug's OLDER child
 * protocol, an {@code attachedChildSession} request naming a loopback port
 * it dials itself, opening a new session with {@code initialize} and a bare
 * {@code attach}. A {@link ChildRelay} is that port: it accepts the platform
 * once, dials the adapter, and pumps both ways, rewriting the bare attach
 * into the launch that names the pending target and answering any
 * {@code startDebugging} the adapter raises on that link with yet another
 * relay — so grandchildren work by construction.
 *
 * Every outgoing frame gets a fresh per-link {@code seq}; responses map
 * back to the client's original seq via per-link pending tables. All
 * threads are named daemons; nothing here ever touches the EDT.
 */
public final class DapProxy {

    private static final Logger LOG = Logger.getLogger(DapProxy.class.getName());
    /** Pending-table marker: the proxy itself sent this request. */
    private static final int PROXY = -1;

    private final Socket parentSocket;
    private final Socket proxySideClient;
    private final Socket actionSideClient;
    private final int adapterPort;
    private final Runnable onClosed;

    private volatile Socket childSocket;
    private volatile boolean spliced;
    /** Session over: pumps stopped, client half-closed. Set once. */
    private final AtomicBoolean sessionEnded = new AtomicBoolean();
    /** onClosed fires exactly once, from whichever path gets there first. */
    private final AtomicBoolean adapterStopped = new AtomicBoolean();
    private volatile JSONObject childConfiguration;
    private final AtomicBoolean closed = new AtomicBoolean();
    /** Every extra target handed to the platform as its own session. */
    private final List<ChildRelay> relays = new CopyOnWriteArrayList<>();
    /** How long a relay waits for the platform to dial its port. */
    static final int ACCEPT_TIMEOUT_MS = 30_000;

    private final Object toClientLock = new Object();
    private final Object toParentLock = new Object();
    private final Object toChildLock = new Object();
    private int toClientSeq;
    private int toParentSeq;
    private int toChildSeq;
    private final Map<Integer, Integer> parentPending = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> childPending = new ConcurrentHashMap<>();
    /** Latest setBreakpoints arguments per source path, replayed to the child. */
    private final Map<String, JSONObject> breakpointsBySource = new ConcurrentHashMap<>();
    private volatile JSONObject exceptionBreakpoints;

    private DapProxy(Socket parentSocket, Socket proxySideClient,
            Socket actionSideClient, int adapterPort, Runnable onClosed) {
        this.parentSocket = parentSocket;
        this.proxySideClient = proxySideClient;
        this.actionSideClient = actionSideClient;
        this.adapterPort = adapterPort;
        this.onClosed = onClosed;
    }

    /**
     * Dials the adapter, builds the loopback socket pair whose action side
     * is handed to {@code DAPConfiguration}, and starts the pumps.
     */
    public static DapProxy start(int adapterPort, Runnable onClosed) throws IOException {
        Socket parent = dial(adapterPort);
        Socket proxySide = null;
        Socket actionSide = null;
        try (ServerSocket rendezvous = new ServerSocket(
                0, 1, InetAddress.getLoopbackAddress())) {
            actionSide = new Socket(InetAddress.getLoopbackAddress(),
                    rendezvous.getLocalPort());
            proxySide = rendezvous.accept();
        } catch (IOException ex) {
            parent.close();
            if (actionSide != null) {
                actionSide.close();
            }
            throw ex;
        }
        DapProxy proxy = new DapProxy(parent, proxySide, actionSide, adapterPort, onClosed);
        proxy.pump("nmox-dap-client", proxySide, proxy::onClientFrame);
        proxy.pump("nmox-dap-parent", parent, proxy::onParentFrame);
        return proxy;
    }

    /**
     * Visible for tests: true once the child dance has completed and client
     * requests route to the child. The flag flips just after the proxy writes
     * {@code configurationDone}, so a test that infers the splice from that
     * frame's arrival on the child can race ahead of it — await this instead.
     */
    boolean spliced() {
        return spliced;
    }

    /** Visible for tests: the loopback pair is fully reaped — the per-session
     *  FD leak ledger 55 M1 named. True only after the client closed its
     *  socket and the client pump swept up on clean EOF. */
    boolean clientPairClosed() {
        return proxySideClient.isClosed() && actionSideClient.isClosed();
    }

    /** Visible for tests: the extra targets opened as sessions of their own. */
    int childSessions() {
        return relays.size();
    }

    /** Stream pair for {@code DAPConfiguration.create} — the client side. */
    public InputStream clientInput() throws IOException {
        return actionSideClient.getInputStream();
    }

    public OutputStream clientOutput() throws IOException {
        return actionSideClient.getOutputStream();
    }

    // --- routing ---------------------------------------------------------

    private void onClientFrame(JSONObject frame) throws IOException {
        if (!"request".equals(frame.optString("type"))) {
            if ("response".equals(frame.optString("type"))
                    && "attachedChildSession".equals(frame.optString("command"))) {
                noteChildSessionAnswer(frame);
            } else {
                LOG.log(Level.FINE, "dropping non-request from client: {0}", frame.optString("type"));
            }
            return;
        }
        String command = frame.optString("command");
        int clientSeq = frame.optInt("seq");
        switch (command) {
            case "disconnect", "terminate" -> {
                // Both connections must hear it; the client gets one reply.
                // Gated on childSocket, NOT spliced: a disconnect can land
                // while the child dance is still running (spliced flips only
                // after the initialized/replay handshake), and a child that
                // exists but never hears disconnect keeps the debuggee
                // alive. Found by the Windows lane, where the runner's
                // scheduling lands the client's disconnect in that window.
                if (childSocket != null) {
                    send(Link.CHILD, frame, clientSeq);
                    send(Link.PARENT, frame, PROXY);
                } else {
                    send(Link.PARENT, frame, clientSeq);
                }
            }
            case "setBreakpoints" -> {
                cacheBreakpoints(frame);
                routeConfigRequest(frame, clientSeq);
            }
            case "setExceptionBreakpoints" -> {
                exceptionBreakpoints = frame.optJSONObject("arguments");
                routeConfigRequest(frame, clientSeq);
            }
            default -> {
                if (spliced && childSocket != null) {
                    send(Link.CHILD, frame, clientSeq);
                } else {
                    send(Link.PARENT, frame, clientSeq);
                }
            }
        }
    }

    /** Breakpoint-ish requests go wherever the client's reply comes from,
     *  plus a fire-and-forget copy so both sessions agree. */
    private void routeConfigRequest(JSONObject frame, int clientSeq) throws IOException {
        if (spliced && childSocket != null) {
            send(Link.CHILD, frame, clientSeq);
            send(Link.PARENT, frame, PROXY);
        } else {
            send(Link.PARENT, frame, clientSeq);
        }
    }

    private void onParentFrame(JSONObject frame) throws IOException {
        switch (frame.optString("type")) {
            case "response" -> {
                Integer origin = parentPending.remove(frame.optInt("request_seq"));
                if (origin != null && origin != PROXY) {
                    forwardToClient(frame, origin);
                }
            }
            case "event" -> {
                forwardToClient(frame, null);
                if ("terminated".equals(frame.optString("event"))) {
                    endSession();
                }
            }
            case "request" -> onReverseRequest(Link.PARENT, frame);
            default -> LOG.log(Level.FINE, "unknown parent frame type");
        }
    }

    private void onChildFrame(JSONObject frame) throws IOException {
        switch (frame.optString("type")) {
            case "response" -> {
                Integer origin = childPending.remove(frame.optInt("request_seq"));
                if (origin == null) {
                    return;
                }
                if (origin == PROXY) {
                    onChildDanceResponse(frame);
                } else {
                    forwardToClient(frame, origin);
                }
            }
            case "event" -> {
                String event = frame.optString("event");
                if ("initialized".equals(event)) {
                    // the client already ran its configuration phase against
                    // the parent — replay its outcome, never the event
                    replayConfiguration();
                } else {
                    forwardToClient(frame, null);
                    if ("terminated".equals(event)) {
                        endSession();
                    }
                }
            }
            case "request" -> onReverseRequest(Link.CHILD, frame);
            default -> LOG.log(Level.FINE, "unknown child frame type");
        }
    }

    // --- the child dance --------------------------------------------------

    private void onReverseRequest(Link from, JSONObject frame) throws IOException {
        String command = frame.optString("command");
        if (!"startDebugging".equals(command)) {
            respond(from, frame, false, "unsupported by NMOX DAP proxy");
            LOG.log(Level.FINE, "declined reverse request {0}", command);
            return;
        }
        // parse BEFORE acking success: a malformed configuration used to be
        // acked first, so the parent believed a child launched that never
        // would (the getJSONObject throw landed in the pump's catch)
        JSONObject configuration;
        try {
            configuration = frame.getJSONObject("arguments")
                    .getJSONObject("configuration");
        } catch (org.json.JSONException ex) {
            respond(from, frame, false, "malformed startDebugging configuration");
            LOG.log(Level.INFO, "refused malformed startDebugging", ex);
            return;
        }
        if (childSocket != null) {
            // the program's OWN process is the flat session above; every
            // further target (a forked child, a worker) gets a session of
            // its own through the platform's child-session door
            respond(from, frame, true, null);
            spawnChildSession(configuration, requestKind(frame), f -> forwardToClient(f, null));
            return;
        }
        respond(from, frame, true, null);
        childConfiguration = configuration;
        Socket child = dial(adapterPort);
        childSocket = child;
        pump("nmox-dap-child", child, this::onChildFrame);
        JSONObject init = new JSONObject()
                .put("type", "request").put("command", "initialize")
                .put("arguments", new JSONObject()
                        .put("clientID", "nmox").put("adapterID", "nmox-proxy")
                        .put("pathFormat", "path")
                        .put("linesStartAt1", true).put("columnsStartAt1", true)
                        .put("supportsRunInTerminalRequest", false));
        send(Link.CHILD, init, PROXY);
    }

    /** js-debug says whether the target wants {@code launch} or {@code attach}. */
    private static String requestKind(JSONObject startDebugging) {
        JSONObject args = startDebugging.optJSONObject("arguments");
        String kind = args == null ? "" : args.optString("request", "");
        return "attach".equals(kind) ? "attach" : "launch";
    }

    /**
     * Opens a relay for one extra target and asks the platform — on the
     * link {@code platformWriter} writes to — to open a session on it. The
     * request shape is the platform's own {@code attachedChildSession}
     * contract: the port as a STRING (it parses it) and a name for the
     * session tab.
     */
    private void spawnChildSession(JSONObject configuration, String request,
            FrameWriter platformWriter) throws IOException {
        ChildRelay relay = new ChildRelay(configuration, request);
        relays.add(relay);
        relay.start();
        platformWriter.write(new JSONObject()
                .put("type", "request").put("command", "attachedChildSession")
                .put("arguments", new JSONObject().put("config", new JSONObject()
                        .put("__jsDebugChildServer", String.valueOf(relay.port()))
                        .put("name", configuration.optString("name", "child")))));
        LOG.log(Level.FINE, "child session offered on port {0}", relay.port());
    }

    private static void noteChildSessionAnswer(JSONObject response) {
        if (!response.optBoolean("success")) {
            // the adapter was already told its target may start, so a
            // declined offer leaves that target waiting for a session that
            // never comes (the ledger 39 shape, for this one case); the relay
            // closes itself when nobody dials — say why, where a person looks
            LOG.log(Level.WARNING, "the platform declined a child debug session ({0}); "
                    + "that child process or worker will stay paused until the run is finished",
                    response.optString("message", "no reason given"));
        }
    }

    private interface FrameWriter {
        void write(JSONObject frame) throws IOException;
    }

    /**
     * One extra target as the platform's own session. The platform dials
     * {@link #port()} (once), we dial the adapter, and the two are pumped
     * against each other with three translations: the platform's bare
     * {@code attach} becomes the {@code launch}/{@code attach} carrying the
     * target's configuration (its {@code __pendingTargetId} is what the
     * adapter matches); the platform's answer to our
     * {@code attachedChildSession} is ours and never reaches the adapter;
     * and a {@code startDebugging} the adapter raises on THIS link spawns
     * another relay, offered on this link's platform session.
     */
    private final class ChildRelay {
        private final ServerSocket server;
        private final JSONObject configuration;
        private final String request;
        private final Object toPlatformLock = new Object();
        private final Object toAdapterLock = new Object();
        /** Our own reverse requests and answers on the adapter link — far
         *  above any seq the adapter or the platform will reach. */
        private int injected = 1_000_000;
        /** The seq of the platform's bare attach, so its answer comes back
         *  under the command the platform sent, not the one the adapter saw. */
        private volatile int attachSeq = Integer.MIN_VALUE;
        private volatile Socket platform;
        private volatile Socket adapter;

        ChildRelay(JSONObject configuration, String request) throws IOException {
            this.configuration = configuration;
            this.request = request;
            this.server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            this.server.setSoTimeout(ACCEPT_TIMEOUT_MS);
        }

        int port() {
            return server.getLocalPort();
        }

        void start() {
            Threads.daemon(this::accept, "nmox-dap-child-server").start();
        }

        private void accept() {
            Socket p;
            try {
                p = server.accept();
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "the platform never dialed the child debug session offered on port "
                        + port() + " within " + ACCEPT_TIMEOUT_MS / 1000 + " s; the target \""
                        + configuration.optString("name", "child")
                        + "\" will stay paused until the run is finished", ex);
                return;
            } finally {
                closeServer();   // one-shot: the port exists for one session
            }
            platform = p;
            try {
                adapter = dial(adapterPort);
            } catch (IOException ex) {
                LOG.log(Level.INFO, "could not reach the adapter for a child session", ex);
                closeQuietly(p);
                return;
            }
            pumpFrames("nmox-dap-child-platform", platform, this::onPlatformFrame, () -> {
                closeQuietly(adapter);
                closeQuietly(platform);
            });
            pumpFrames("nmox-dap-child-adapter", adapter, this::onAdapterFrame, () -> {
                // half-close so the platform drains what we wrote (the
                // terminated event is the last thing) before it reads EOF
                try {
                    platform.shutdownOutput();
                } catch (IOException ex) {
                    LOG.log(Level.FINE, "child session half-close failed", ex);
                }
                closeQuietly(adapter);
            });
        }

        private void onPlatformFrame(JSONObject frame) throws IOException {
            String type = frame.optString("type");
            if ("response".equals(type)
                    && "attachedChildSession".equals(frame.optString("command"))) {
                noteChildSessionAnswer(frame);
                return;
            }
            if ("request".equals(type) && "attach".equals(frame.optString("command"))) {
                attachSeq = frame.optInt("seq", Integer.MIN_VALUE);
                JSONObject copy = new JSONObject(frame.toString());
                copy.put("command", request);
                copy.put("arguments", new JSONObject(configuration.toString()));
                writeAdapter(copy);
                return;
            }
            writeAdapter(frame);
        }

        private void onAdapterFrame(JSONObject frame) throws IOException {
            if (!"request".equals(frame.optString("type"))) {
                // attachSeq is MIN_VALUE until the platform's attach arrives,
                // and optInt answers MIN_VALUE for a response missing its
                // request_seq — never let those two sentinels match
                if ("response".equals(frame.optString("type"))
                        && attachSeq != Integer.MIN_VALUE
                        && frame.optInt("request_seq", Integer.MIN_VALUE) == attachSeq) {
                    JSONObject copy = new JSONObject(frame.toString());
                    copy.put("command", "attach");
                    writePlatform(copy);
                    return;
                }
                writePlatform(frame);
                return;
            }
            String command = frame.optString("command");
            if (!"startDebugging".equals(command)) {
                answerAdapter(frame, false, "unsupported by NMOX DAP proxy");
                return;
            }
            JSONObject configuration;
            try {
                configuration = frame.getJSONObject("arguments").getJSONObject("configuration");
            } catch (org.json.JSONException ex) {
                answerAdapter(frame, false, "malformed startDebugging configuration");
                return;
            }
            answerAdapter(frame, true, null);
            spawnChildSession(configuration, requestKind(frame), this::writeInjected);
        }

        private void answerAdapter(JSONObject request, boolean success, String message)
                throws IOException {
            JSONObject response = new JSONObject()
                    .put("type", "response")
                    .put("command", request.optString("command"))
                    .put("request_seq", request.optInt("seq"))
                    .put("success", success);
            if (message != null) {
                response.put("message", message);
            }
            synchronized (toAdapterLock) {
                response.put("seq", injected++);
                DapFrames.write(adapter.getOutputStream(), response.toString());
            }
        }

        private void writeInjected(JSONObject frame) throws IOException {
            synchronized (toPlatformLock) {
                frame.put("seq", injected++);
                DapFrames.write(platform.getOutputStream(), frame.toString());
            }
        }

        private void writePlatform(JSONObject frame) throws IOException {
            synchronized (toPlatformLock) {
                DapFrames.write(platform.getOutputStream(), frame.toString());
            }
        }

        private void writeAdapter(JSONObject frame) throws IOException {
            synchronized (toAdapterLock) {
                DapFrames.write(adapter.getOutputStream(), frame.toString());
            }
        }

        void close() {
            closeServer();
            closeQuietly(adapter);
            closeQuietly(platform);
        }

        private void closeServer() {
            try {
                server.close();
            } catch (IOException ignored) {
                // teardown is best-effort by design
            }
        }
    }

    private void onChildDanceResponse(JSONObject response) throws IOException {
        if ("initialize".equals(response.optString("command"))) {
            JSONObject launch = new JSONObject()
                    .put("type", "request").put("command", "launch")
                    .put("arguments", childConfiguration);
            send(Link.CHILD, launch, PROXY);
        }
        // launch/setBreakpoints/configurationDone acks need no action
    }

    private void replayConfiguration() throws IOException {
        for (JSONObject args : breakpointsBySource.values()) {
            send(Link.CHILD, new JSONObject()
                    .put("type", "request").put("command", "setBreakpoints")
                    .put("arguments", args), PROXY);
        }
        if (exceptionBreakpoints != null) {
            send(Link.CHILD, new JSONObject()
                    .put("type", "request").put("command", "setExceptionBreakpoints")
                    .put("arguments", exceptionBreakpoints), PROXY);
        }
        send(Link.CHILD, new JSONObject()
                .put("type", "request").put("command", "configurationDone")
                .put("arguments", new JSONObject()), PROXY);
        spliced = true;
    }

    private void cacheBreakpoints(JSONObject frame) {
        JSONObject args = frame.optJSONObject("arguments");
        if (args == null) {
            return;
        }
        JSONObject source = args.optJSONObject("source");
        String path = source != null ? source.optString("path", "") : "";
        if (!path.isEmpty()) {
            breakpointsBySource.put(path, args);
        }
    }

    // --- plumbing ----------------------------------------------------------

    private enum Link { CLIENT, PARENT, CHILD }

    private void send(Link link, JSONObject frame, int origin) throws IOException {
        JSONObject copy = new JSONObject(frame.toString());
        switch (link) {
            case PARENT -> {
                synchronized (toParentLock) {
                    int seq = ++toParentSeq;
                    copy.put("seq", seq);
                    if ("request".equals(copy.optString("type"))) {
                        parentPending.put(seq, origin);
                    }
                    DapFrames.write(parentSocket.getOutputStream(), copy.toString());
                }
            }
            case CHILD -> {
                synchronized (toChildLock) {
                    int seq = ++toChildSeq;
                    copy.put("seq", seq);
                    if ("request".equals(copy.optString("type"))) {
                        childPending.put(seq, origin);
                    }
                    DapFrames.write(childSocket.getOutputStream(), copy.toString());
                }
            }
            case CLIENT -> throw new IllegalArgumentException("use forwardToClient");
        }
    }

    /** originalClientSeq restores request_seq for responses; null for events. */
    private void forwardToClient(JSONObject frame, Integer originalClientSeq) throws IOException {
        JSONObject copy = new JSONObject(frame.toString());
        synchronized (toClientLock) {
            copy.put("seq", ++toClientSeq);
            if (originalClientSeq != null) {
                copy.put("request_seq", (int) originalClientSeq);
            }
            DapFrames.write(proxySideClient.getOutputStream(), copy.toString());
        }
    }

    private void respond(Link link, JSONObject request, boolean success, String message)
            throws IOException {
        JSONObject response = new JSONObject()
                .put("type", "response")
                .put("command", request.optString("command"))
                .put("request_seq", request.optInt("seq"))
                .put("success", success);
        if (message != null) {
            response.put("message", message);
        }
        send(link, response, PROXY);
    }

    private void pump(String name, Socket socket, FrameHandler handler) {
        boolean[] peerClosed = new boolean[1];
        pumpFrames(name, socket, handler, () -> {
            // a dropped link ends the session; it must not slam the
            // client's socket shut on top of frames it hasn't read
            endSession();
            // ...but once the CLIENT's own socket has hit clean EOF, the
            // client is gone and has read everything it ever will — the
            // loopback pair can be reaped. Nothing else ever closes it in
            // production (close() is the owner's call and no owner holds
            // the proxy), so this is where the per-session FD pair used
            // to leak.
            if (socket == proxySideClient && peerClosed[0]) {
                closeQuietly(proxySideClient);
                closeQuietly(actionSideClient);
            }
        }, peerClosed);
    }

    private void pumpFrames(String name, Socket socket, FrameHandler handler, Runnable onEnd) {
        pumpFrames(name, socket, handler, onEnd, new boolean[1]);
    }

    /** Reads frames until EOF or error; {@code peerClosed[0]} is true on a
     *  clean EOF at a frame boundary when {@code onEnd} runs. */
    private void pumpFrames(String name, Socket socket, FrameHandler handler,
            Runnable onEnd, boolean[] peerClosed) {
        Thread t = Threads.daemon(() -> {
            try {
                InputStream in = socket.getInputStream();
                String json;
                while ((json = DapFrames.read(in)) != null) {
                    try {
                        handler.handle(new JSONObject(json));
                    } catch (RuntimeException ex) {
                        // one bad frame must not kill the session
                        LOG.log(Level.INFO, "DAP frame handling failed", ex);
                    }
                }
                peerClosed[0] = true;   // clean EOF: the far side closed its socket
            } catch (IOException ex) {
                LOG.log(Level.FINE, name + " pump ended", ex);
            } finally {
                onEnd.run();
            }
        }, name);
        t.start();
    }

    private interface FrameHandler {
        void handle(JSONObject frame) throws IOException;
    }

    private static Socket dial(int port) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 5_000);
        return s;
    }

    /**
     * The session is over — `terminated` arrived, or a link dropped. The
     * client must still be able to read what we already wrote to it: closing
     * its socket here discards bytes sitting unread in the receive buffer,
     * and the last thing written is precisely the `terminated` event. (macOS
     * usually lost that race harmlessly because the reader thread had already
     * drained; Linux loses it every time.) So half-close: the FIN queues
     * *behind* the buffered frames, the client drains them, reads EOF, and
     * ends the session on its own terms. The socket pair belongs to whoever
     * took clientInput()/clientOutput() — {@link #close()} is theirs to call.
     */
    private void endSession() {
        if (!sessionEnded.compareAndSet(false, true)) {
            return;
        }
        try {
            proxySideClient.shutdownOutput();
        } catch (IOException ex) {
            LOG.log(Level.FINE, "client half-close failed", ex);
        }
        closeQuietly(childSocket);
        closeQuietly(parentSocket);
        relays.forEach(ChildRelay::close);
        stopAdapter();
    }

    /** Idempotent; closes every socket and fires onClosed exactly once. */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        sessionEnded.set(true);
        closeQuietly(childSocket);
        closeQuietly(parentSocket);
        closeQuietly(proxySideClient);
        closeQuietly(actionSideClient);
        relays.forEach(ChildRelay::close);
        stopAdapter();
    }

    /** onClosed stops the adapter; it must fire exactly once across both paths. */
    private void stopAdapter() {
        if (adapterStopped.compareAndSet(false, true) && onClosed != null) {
            onClosed.run();
        }
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // teardown is best-effort by design
            }
        }
    }
}

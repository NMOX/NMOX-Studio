package org.nmox.studio.editor.debug.dap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
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
            LOG.log(Level.FINE, "dropping non-request from client: {0}", frame.optString("type"));
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
        respond(from, frame, true, null);
        if (childSocket != null) {
            // one child per session; launch config disables auto-attach so
            // this only fires for exotic targets — run them undebugged
            LOG.log(Level.INFO, "ignoring additional js-debug target");
            return;
        }
        childConfiguration = frame.getJSONObject("arguments")
                .getJSONObject("configuration");
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
        Thread t = new Thread(() -> {
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
            } catch (IOException ex) {
                LOG.log(Level.FINE, name + " pump ended", ex);
            } finally {
                // a dropped link ends the session; it must not slam the
                // client's socket shut on top of frames it hasn't read
                endSession();
            }
        }, name);
        t.setDaemon(true);
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

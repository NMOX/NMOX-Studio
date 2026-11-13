package org.nmox.studio.editor.lsp;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openide.util.RequestProcessor;

/**
 * Asks a language server for a file's problems, on behalf of a client
 * that cannot ask (3.10.0).
 *
 * <p>The protocol has two ways for a server to report problems. In the
 * older one the server sends {@code textDocument/publishDiagnostics}
 * whenever it likes. In the newer one (3.17, "pull") the client asks with
 * {@code textDocument/diagnostic} and the server answers. TypeScript 7's
 * server speaks only the newer one for source files: measured on 7.0.2, a
 * file with two type errors was opened and nothing arrived, and the same
 * file asked about returned both. The platform's client (RELEASE310)
 * never asks: nothing in its jar names the request. So the server would
 * give completion, hover and rename, and no red underline, ever.
 *
 * <p>This sits on both streams between the client and the server and
 * does the asking:
 * <ul>
 * <li>Towards the server it reads the client's frames as they pass.
 * When a document is opened, changed or saved it sends a
 * {@code textDocument/diagnostic} of its own a moment later, under an id
 * no client request has ({@link #ID_PREFIX}). A change asks again for
 * every open document, because an edit in one file changes the errors in
 * another (the server says so: {@code interFileDependencies}).</li>
 * <li>From the server it takes the answers to its own requests out of
 * the stream and puts a {@code publishDiagnostics} for that document in
 * their place, which the client paints as it always has, and which
 * {@link DiagnosticsTap} hands to Action Items. A closed document is
 * published empty.</li>
 * <li>It tells the server it can be asked to ask again
 * ({@code workspace/diagnostic/refresh}), answers that request itself,
 * and asks again: this is how an {@code npm install} that finishes, or a
 * {@code tsconfig.json} that changes, clears errors with no keystroke.</li>
 * </ul>
 *
 * <p>Smaller repairs ride the same pass, each measured against
 * TypeScript 7.0.2. The platform's client writes {@code "params":null}
 * on {@code shutdown} and {@code exit} (lsp4j's message writer, read
 * from its bytecode), and this server refuses both with
 * {@code InvalidParams: expected no params, got null}, so it would never
 * be shut down by the protocol; the member is removed. The first frame,
 * {@code initialize}, gains the two capabilities above. The server's
 * request to be told of configuration changes is accepted here
 * ({@link #accepts}), and its info-level log is kept out of the IDE's
 * ({@link #chatter}).
 *
 * <p>Every other byte passes unchanged, in order. A frame is held only
 * until it is whole. Bounds: a header over 8 KiB, or one with no length,
 * loses the framing on that stream, and from then on this passes bytes
 * through and asks nothing; a body over 16 MiB passes without being
 * read; at most 256 open documents are followed and 32 are asked about
 * in one pass; an answer older than one already published for the same
 * document is dropped. Nothing here blocks the client's reader on a
 * write: the reply to a refresh request is written by the same lane that
 * writes the questions.
 */
final class PullDiagnostics {

    /** The ids of this class's own requests. lsp4j's are decimal numbers as strings. */
    static final String ID_PREFIX = "nmox-pull:";

    static final int MAX_HEADER = 8 * 1024;
    static final int MAX_BODY = 16 * 1024 * 1024;
    static final int MAX_OPEN = 256;
    static final int MAX_BATCH = 32;
    static final int MAX_PENDING = 1024;
    static final int MAX_RELATED = 64;
    /** How long after an open a document is asked about. */
    static final int OPEN_MILLIS = 60;
    /** How long after the last change. Typing does not ask on every key. */
    static final int CHANGE_MILLIS = 300;
    /** The longest a document waits while the keys keep coming. */
    static final int MAX_WAIT_MILLIS = 1500;
    static final int MAX_RETRIES = 2;

    private static final Logger LOG = Logger.getLogger(PullDiagnostics.class.getName());
    private static final RequestProcessor LANE = new RequestProcessor("nmox-lsp-pull", 2, false, false);

    private static final byte[] PREFIX_BYTES = ID_PREFIX.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] REFRESH_BYTES = "diagnostic/refresh".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] REFRESH_ESCAPED = "diagnostic\\/refresh".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] REGISTER_BYTES = "registerCapability".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] LOG_BYTES = "window/logMessage".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] LOG_ESCAPED = "window\\/logMessage".getBytes(StandardCharsets.US_ASCII);
    /** The one registration accepted on the client's behalf; see {@link #accepts}. */
    static final String CONFIGURATION_WATCH = "workspace/didChangeConfiguration";

    /** Runs {@link #work} once, some milliseconds from now; arming again moves the time. */
    interface Timer {
        void in(int millis);
    }

    private record Pending(String uri, boolean clear, long seq, int retries) {
    }

    private final String name;
    private final OutputStream rawToServer;
    private final ToServer toServer;
    private final FromServer fromServer;
    private final Timer timer;
    private final LongSupplier millis;

    /** Guards everything below. Never held while writing to the server. */
    private final Object state = new Object();
    private final LinkedHashSet<String> open = new LinkedHashSet<>();
    private final LinkedHashMap<String, Integer> dirty = new LinkedHashMap<>();
    private final LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
    private final Map<String, Long> published = new LinkedHashMap<>();
    private final List<byte[]> outbox = new ArrayList<>();
    private long firstDirtyAt = -1;
    private long nextId;
    private boolean ended;
    /** The client's framing was lost: nothing is asked any more. */
    private volatile boolean deaf;

    /** Serializes whole frames onto the server's input: the client's, and this class's own. */
    private final Object writeLock = new Object();

    PullDiagnostics(OutputStream rawToServer, InputStream rawFromServer, String name) {
        this(rawToServer, rawFromServer, name, null, System::currentTimeMillis);
    }

    /** @param timer null for the real one; a test passes one it steps by hand */
    PullDiagnostics(OutputStream rawToServer, InputStream rawFromServer, String name,
            Timer timer, LongSupplier millis) {
        this.name = name;
        this.rawToServer = rawToServer;
        this.millis = millis;
        this.toServer = new ToServer();
        this.fromServer = new FromServer(rawFromServer);
        if (timer != null) {
            this.timer = timer;
        } else {
            RequestProcessor.Task task = LANE.create(this::work);
            this.timer = task::schedule;
        }
    }

    /** What the client writes to. */
    OutputStream toServer() {
        return toServer;
    }

    /** What the client reads from. */
    InputStream fromServer() {
        return fromServer;
    }

    // ------------------------------------------------------------------ the client's side

    /** A frame the client wrote, whole. Returns the body to send: the same array when nothing changed. */
    private byte[] clientFrame(byte[] body, boolean first) {
        if (body.length > MAX_BODY) {
            return body;
        }
        boolean init = first && contains(body, "\"initialize\"");
        boolean done = contains(body, "\"shutdown\"") || contains(body, "\"exit\"");
        boolean doc = contains(body, "/didOpen\"") || contains(body, "/didChange\"")
                || contains(body, "/didSave\"") || contains(body, "/didClose\"");
        if (!init && !done && !doc) {
            return body;
        }
        JSONObject message;
        try {
            message = new JSONObject(new String(body, StandardCharsets.UTF_8));
        } catch (RuntimeException notJson) {
            return body; // a message this cannot read is the client's and the server's business
        }
        String method = message.optString("method", "");
        if (init && "initialize".equals(method)) {
            return declare(message).toString().getBytes(StandardCharsets.UTF_8);
        }
        if (done && ("shutdown".equals(method) || "exit".equals(method))
                && message.has("params") && message.isNull("params")) {
            message.remove("params");
            return message.toString().getBytes(StandardCharsets.UTF_8);
        }
        if (doc) {
            JSONObject params = message.optJSONObject("params");
            JSONObject document = params == null ? null : params.optJSONObject("textDocument");
            String uri = document == null ? "" : document.optString("uri", "");
            if (!uri.isEmpty()) {
                observed(method, uri);
            }
        }
        return body;
    }

    /** The two capabilities this class provides on the client's behalf. */
    static JSONObject declare(JSONObject initialize) {
        JSONObject params = child(initialize, "params");
        JSONObject capabilities = child(params, "capabilities");
        child(child(capabilities, "workspace"), "diagnostics").put("refreshSupport", true);
        child(child(capabilities, "textDocument"), "diagnostic")
                .put("dynamicRegistration", false).put("relatedDocumentSupport", true);
        return initialize;
    }

    private static JSONObject child(JSONObject parent, String key) {
        JSONObject child = parent.optJSONObject(key);
        if (child == null) {
            child = new JSONObject();
            parent.put(key, child);
        }
        return child;
    }

    private void observed(String method, String uri) {
        int delay;
        synchronized (state) {
            if (ended) {
                return;
            }
            switch (method) {
                case "textDocument/didOpen" -> {
                    if (!open.contains(uri) && open.size() >= MAX_OPEN) {
                        Iterator<String> oldest = open.iterator();
                        dirty.remove(oldest.next());
                        oldest.remove();
                    }
                    open.add(uri);
                    mark(uri);
                    delay = OPEN_MILLIS;
                }
                case "textDocument/didChange", "textDocument/didSave" -> {
                    // the changed document first, then the ones its edit may have broken
                    mark(uri);
                    for (String other : open) {
                        mark(other);
                    }
                    delay = CHANGE_MILLIS;
                }
                case "textDocument/didClose" -> {
                    open.remove(uri);
                    dirty.remove(uri);
                    // asked once more so that an answer comes back to hang the empty list on
                    outbox.add(question(uri, true, 0));
                    delay = 0;
                }
                default -> {
                    return;
                }
            }
            delay = within(delay);
        }
        timer.in(delay);
    }

    /**
     * Under {@link #state}: a delay cut to what is left of the longest
     * wait. Arming the timer moves its time, so whatever arms it
     * again and again (keys, or a server's requests) must count from
     * the first thing left waiting, or the lane never runs.
     */
    private int within(int delay) {
        if (firstDirtyAt < 0) {
            firstDirtyAt = millis.getAsLong();
        }
        long waited = millis.getAsLong() - firstDirtyAt;
        return (int) Math.max(0, Math.min(delay, MAX_WAIT_MILLIS - waited));
    }

    /** Under {@link #state}. */
    private void mark(String uri) {
        if (open.contains(uri)) {
            dirty.putIfAbsent(uri, 0);
        }
    }

    /** Under {@link #state}: the frame that asks about a document, registered as waiting for its answer. */
    private byte[] question(String uri, boolean clear, int retries) {
        String id = ID_PREFIX + (++nextId);
        if (pending.size() >= MAX_PENDING) {
            Iterator<String> oldest = pending.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        pending.put(id, new Pending(uri, clear, nextId, retries));
        JSONObject request = new JSONObject().put("jsonrpc", "2.0").put("id", id)
                .put("method", "textDocument/diagnostic")
                .put("params", new JSONObject().put("textDocument", new JSONObject().put("uri", uri)));
        return frame(request.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** The lane's one job: write what is waiting to be written, then ask about what changed. */
    void work() {
        List<byte[]> frames;
        boolean more;
        synchronized (state) {
            if (ended || deaf) {
                outbox.clear();
                dirty.clear();
                return;
            }
            frames = new ArrayList<>(outbox);
            outbox.clear();
            Iterator<Map.Entry<String, Integer>> it = dirty.entrySet().iterator();
            for (int n = 0; it.hasNext() && n < MAX_BATCH; n++) {
                Map.Entry<String, Integer> entry = it.next();
                it.remove();
                frames.add(question(entry.getKey(), false, entry.getValue()));
            }
            more = !dirty.isEmpty();
            // asked: the wait starts over, for the documents left as for the rest. Left
            // counting from before this pass, the longest wait stays spent, every change
            // that follows arms the timer at zero, and a held key is a pass per key
            firstDirtyAt = more ? millis.getAsLong() : -1;
        }
        try {
            synchronized (writeLock) {
                for (byte[] frame : frames) {
                    rawToServer.write(frame);
                }
                rawToServer.flush();
            }
        } catch (IOException gone) {
            LOG.log(Level.FINE, "{0}: the server''s input is closed; nothing more is asked ({1})",
                    new Object[] {name, gone.toString()});
            synchronized (state) {
                ended = true;
            }
            return;
        }
        if (more) {
            timer.in(OPEN_MILLIS);
        }
    }

    private final class ToServer extends OutputStream {

        private final ByteArrayOutputStream held = new ByteArrayOutputStream();
        private boolean first = true;
        /** How many held bytes make the frame being read whole; 0 while its header is not. */
        private int need;

        @Override
        public void write(int b) throws IOException {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (deaf) {
                synchronized (writeLock) {
                    if (held.size() > 0) {
                        held.writeTo(rawToServer); // the framing was lost on the other stream, mid-frame here
                        held.reset();
                    }
                    rawToServer.write(b, off, len);
                }
                return;
            }
            held.write(b, off, len);
            while (held.size() > 0 && held.size() >= need) {
                byte[] buf = held.toByteArray();
                int headerEnd = blankLine(buf);
                if (headerEnd < 0) {
                    if (buf.length > MAX_HEADER) {
                        lost(buf, "a header over " + MAX_HEADER + " bytes");
                    }
                    return;
                }
                int length = DiagnosticsTap.contentLength(new String(buf, 0, headerEnd, StandardCharsets.US_ASCII));
                if (length < 0) {
                    lost(buf, "a frame without Content-Length");
                    return;
                }
                int bodyStart = headerEnd + separator(buf, headerEnd);
                if (buf.length - bodyStart < length) {
                    need = bodyStart + length; // a large body arriving in pieces is not re-read per piece
                    return;
                }
                need = 0;
                byte[] body = java.util.Arrays.copyOfRange(buf, bodyStart, bodyStart + length);
                byte[] sent = clientFrame(body, first);
                first = false;
                synchronized (writeLock) {
                    if (sent == body) {
                        rawToServer.write(buf, 0, bodyStart + length); // the client's own bytes
                    } else {
                        rawToServer.write(frame(sent));
                    }
                }
                held.reset();
                held.write(buf, bodyStart + length, buf.length - bodyStart - length);
            }
        }

        /** The framing cannot be followed: everything held goes out as it came, and so does the rest. */
        private void lost(byte[] buf, String why) throws IOException {
            deaf = true;
            held.reset();
            LOG.log(Level.INFO, "{0}: the client wrote {1}; its problems are no longer asked for", new Object[] {name, why});
            synchronized (writeLock) {
                rawToServer.write(buf);
            }
        }

        @Override
        public void flush() throws IOException {
            synchronized (writeLock) {
                rawToServer.flush();
            }
        }

        @Override
        public void close() throws IOException {
            synchronized (state) {
                ended = true;
            }
            rawToServer.close();
        }
    }

    // ------------------------------------------------------------------ the server's side

    /** An answer to one of this class's questions: the frame for the client, or null for none. */
    private byte[] answer(JSONObject message) {
        String id = message.optString("id", "");
        Pending asked;
        boolean stillOpen;
        synchronized (state) {
            asked = pending.remove(id);
            if (asked == null) {
                return null; // forgotten: too many were waiting
            }
            stillOpen = open.contains(asked.uri());
            if (asked.clear()) {
                if (stillOpen) {
                    return null; // closed and opened again: the newer question's answer stands
                }
                published.remove(asked.uri());
                return publish(asked.uri(), new JSONArray());
            }
            if (!stillOpen) {
                return null;
            }
            JSONObject error = message.optJSONObject("error");
            if (error != null) {
                int code = error.optInt("code");
                boolean again = code == SilentServerErrors.CONTENT_MODIFIED || code == SilentServerErrors.SERVER_CANCELLED;
                if (again && asked.retries() < MAX_RETRIES) {
                    dirty.putIfAbsent(asked.uri(), asked.retries() + 1);
                    if (firstDirtyAt < 0) {
                        firstDirtyAt = millis.getAsLong();
                    }
                } else {
                    LOG.log(Level.FINE, "{0}: asked about {1}, answered {2}", new Object[] {name, asked.uri(), error});
                    return null;
                }
            } else {
                Long newest = published.get(asked.uri());
                if (newest != null && newest > asked.seq()) {
                    return null; // a later question was answered first
                }
            }
        }
        if (message.has("error")) {
            timer.in(CHANGE_MILLIS);
            return null;
        }
        JSONObject report = message.optJSONObject("result");
        if (report == null || !"full".equals(report.optString("kind", "full"))) {
            return null; // unchanged: what the client shows is still right
        }
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        synchronized (state) {
            if (published.size() >= 4 * MAX_OPEN) {
                published.clear(); // only an ordering guard: forgetting it costs nothing but the guard
            }
            published.put(asked.uri(), asked.seq());
        }
        byte[] own = publish(asked.uri(), report.optJSONArray("items"));
        frames.write(own, 0, own.length);
        JSONObject related = report.optJSONObject("relatedDocuments");
        if (related != null) {
            int n = 0;
            for (String uri : related.keySet()) {
                JSONObject other = related.optJSONObject(uri);
                if (other == null || !"full".equals(other.optString("kind")) || n++ >= MAX_RELATED) {
                    continue;
                }
                byte[] also = publish(clientSpelling(uri), other.optJSONArray("items"));
                frames.write(also, 0, also.length);
            }
        }
        return frames.toByteArray();
    }

    /**
     * The client's own spelling of a document the server names. On a
     * file system that ignores case this server lower-cases the paths it
     * reports ({@code /users/…} for {@code /Users/…}, measured), and a
     * problem filed under the second spelling would be a second file.
     */
    private String clientSpelling(String uri) {
        synchronized (state) {
            for (String mine : open) {
                if (mine.equalsIgnoreCase(uri)) {
                    return mine;
                }
            }
        }
        return uri;
    }

    private static byte[] publish(String uri, JSONArray items) {
        JSONObject notification = new JSONObject().put("jsonrpc", "2.0")
                .put("method", "textDocument/publishDiagnostics")
                .put("params", new JSONObject().put("uri", uri).put("diagnostics", items == null ? new JSONArray() : items));
        return frame(notification.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Whether a request to register capabilities is one this class
     * answers yes to, in the client's place.
     *
     * <p>TypeScript 7 asks, on {@code initialized}, to be told when the
     * configuration changes. The platform's client implements no
     * registration at all (ledger 45), so lsp4j answers with an internal
     * error and logs it at SEVERE, and the server logs the failure back
     * as an error: three red entries for every start, measured in the
     * first walk of this class. The promise that request asks for is one
     * this client keeps by doing nothing: it has no configuration, so it
     * never changes, and there is never a change to report. Any other
     * registration (a file watch, a save hook) is a promise this client
     * would break, and goes to the client to be refused as before.
     */
    static boolean accepts(JSONObject request) {
        JSONObject params = request.optJSONObject("params");
        JSONArray registrations = params == null ? null : params.optJSONArray("registrations");
        if (registrations == null || registrations.isEmpty()) {
            return false;
        }
        for (int i = 0; i < registrations.length(); i++) {
            JSONObject registration = registrations.optJSONObject(i);
            if (registration == null || !CONFIGURATION_WATCH.equals(registration.optString("method"))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a log message is the server talking to itself. TypeScript
     * 7 reports every snapshot it takes, every watch it adds and the
     * whole of the client's capabilities at the "info" level; the
     * platform's client writes each to the IDE's log, and one opened file
     * was three hundred lines of it, with more on every key. Errors and
     * warnings (types 1 and 2) are the client's to show. The rest can be
     * read at FINE under this class's name.
     */
    private boolean chatter(JSONObject message) {
        JSONObject params = message.optJSONObject("params");
        if (params == null || params.optInt("type", 1) < 3) {
            return false;
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "{0}: {1}", new Object[] {name, params.optString("message")});
        }
        return true;
    }

    /** Answers a request of the server's with an empty success, from the lane. */
    private void reply(JSONObject request) {
        JSONObject reply = new JSONObject().put("jsonrpc", "2.0").put("id", request.get("id")).put("result", JSONObject.NULL);
        synchronized (state) {
            if (ended) {
                return;
            }
            outbox.add(frame(reply.toString().getBytes(StandardCharsets.UTF_8)));
        }
        timer.in(0);
    }

    /** The server asked to be asked again: answer it, and ask about every open document. */
    private void refresh(JSONObject request) {
        JSONObject reply = new JSONObject().put("jsonrpc", "2.0").put("id", request.get("id")).put("result", JSONObject.NULL);
        int delay;
        synchronized (state) {
            if (ended) {
                return;
            }
            outbox.add(frame(reply.toString().getBytes(StandardCharsets.UTF_8)));
            for (String uri : open) {
                dirty.putIfAbsent(uri, 0);
            }
            // a server that asks again every few milliseconds (a storm of file
            // changes) would otherwise push its own replies away for as long as it asks
            delay = within(OPEN_MILLIS);
        }
        timer.in(delay);
    }

    private final class FromServer extends InputStream {

        private final InputStream in;
        private byte[] out = new byte[0];
        private int at;
        /** Bytes of an oversize body still to copy straight through. */
        private int passing;
        /** The server's framing was lost: bytes pass, nothing is read. */
        private boolean blind;
        private boolean eof;

        FromServer(InputStream raw) {
            this.in = raw instanceof BufferedInputStream ? raw : new BufferedInputStream(raw, 16 * 1024);
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            for (;;) {
                if (at < out.length) {
                    int n = Math.min(len, out.length - at);
                    System.arraycopy(out, at, b, off, n);
                    at += n;
                    return n;
                }
                if (passing > 0 || blind) {
                    int n = in.read(b, off, blind ? len : Math.min(len, passing));
                    if (n < 0) {
                        return end();
                    }
                    if (!blind) {
                        passing -= n;
                    }
                    return n;
                }
                if (eof) {
                    return end();
                }
                next();
            }
        }

        @Override
        public int available() throws IOException {
            return out.length - at;
        }

        private int end() {
            eof = true;
            synchronized (state) {
                ended = true;
            }
            return -1;
        }

        /** Reads one frame from the server and decides what the client sees of it. */
        private void next() throws IOException {
            ByteArrayOutputStream header = new ByteArrayOutputStream(64);
            int tail = 0;
            for (;;) {
                int c = in.read();
                if (c < 0) {
                    out = header.toByteArray(); // whatever came before the end is the client's
                    at = 0;
                    eof = true;
                    return;
                }
                header.write(c);
                tail = (tail << 8) | c;
                if (tail == 0x0D0A0D0A || (tail & 0xFFFF) == 0x0A0A) {
                    break;
                }
                if (header.size() > MAX_HEADER) {
                    blind(header, "a header over " + MAX_HEADER + " bytes");
                    return;
                }
            }
            int length = DiagnosticsTap.contentLength(header.toString(StandardCharsets.US_ASCII));
            if (length < 0) {
                blind(header, "a frame without Content-Length");
                return;
            }
            if (length > MAX_BODY) {
                out = header.toByteArray();
                at = 0;
                passing = length;
                return;
            }
            byte[] body = in.readNBytes(length);
            byte[] replaced = body.length == length ? serverFrame(body) : body;
            if (replaced == body) {
                header.write(body, 0, body.length);
                out = header.toByteArray();
            } else {
                out = replaced; // an answer of ours: its place is taken, or left empty
            }
            at = 0;
            if (body.length < length) {
                eof = true;
            }
        }

        private void blind(ByteArrayOutputStream header, String why) {
            blind = true;
            deaf = true;
            out = header.toByteArray();
            at = 0;
            LOG.log(Level.INFO, "{0}: the server wrote {1}; its problems are no longer asked for", new Object[] {name, why});
        }

        @Override
        public void close() throws IOException {
            synchronized (state) {
                ended = true;
            }
            in.close();
        }
    }

    /** A frame the server wrote, whole. The same array to pass it on; other bytes (or none) to replace it. */
    private byte[] serverFrame(byte[] body) {
        boolean mine = indexOf(body, PREFIX_BYTES) >= 0;
        boolean asksAgain = indexOf(body, REFRESH_BYTES) >= 0 || indexOf(body, REFRESH_ESCAPED) >= 0;
        boolean registers = indexOf(body, REGISTER_BYTES) >= 0;
        boolean logs = indexOf(body, LOG_BYTES) >= 0 || indexOf(body, LOG_ESCAPED) >= 0;
        if (!mine && !asksAgain && !registers && !logs) {
            return body;
        }
        JSONObject message;
        try {
            message = new JSONObject(new String(body, StandardCharsets.UTF_8));
        } catch (RuntimeException notJson) {
            return body;
        }
        String method = message.optString("method");
        if (logs && "window/logMessage".equals(method) && chatter(message)) {
            return new byte[0];
        }
        if (registers && "client/registerCapability".equals(method) && message.has("id") && accepts(message)) {
            reply(message);
            return new byte[0];
        }
        if (asksAgain && "workspace/diagnostic/refresh".equals(method) && message.has("id")) {
            refresh(message);
            return new byte[0];
        }
        if (mine && !message.has("method") && message.opt("id") instanceof String id && id.startsWith(ID_PREFIX)) {
            try {
                byte[] frames = answer(message);
                return frames == null ? new byte[0] : frames;
            } catch (RuntimeException unexpected) {
                LOG.log(Level.FINE, name + ": an answer that could not be read", unexpected);
                return new byte[0];
            }
        }
        return body;
    }

    // ------------------------------------------------------------------ frames

    static byte[] frame(byte[] body) {
        byte[] header = ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] whole = new byte[header.length + body.length];
        System.arraycopy(header, 0, whole, 0, header.length);
        System.arraycopy(body, 0, whole, header.length, body.length);
        return whole;
    }

    /** The index where the blank line that ends a header begins, or -1. */
    private static int blankLine(byte[] buf) {
        for (int i = 0; i + 1 < buf.length; i++) {
            if (buf[i] == '\n' && buf[i + 1] == '\n') {
                return i;
            }
            if (i + 3 < buf.length && buf[i] == '\r' && buf[i + 1] == '\n' && buf[i + 2] == '\r' && buf[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static int separator(byte[] buf, int headerEnd) {
        return buf[headerEnd] == '\r' ? 4 : 2;
    }

    private static boolean contains(byte[] haystack, String needle) {
        return indexOf(haystack, needle.getBytes(StandardCharsets.US_ASCII)) >= 0;
    }

    static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}

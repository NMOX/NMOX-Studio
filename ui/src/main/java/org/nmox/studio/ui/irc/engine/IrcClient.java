package org.nmox.studio.ui.irc.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.nmox.studio.ui.irc.protocol.Ctcp;
import org.nmox.studio.ui.irc.protocol.IrcMessage;
import org.openide.util.RequestProcessor;

/**
 * One IRC server connection — the engine between the pure protocol
 * classes and the Swing window. Owns the socket, the registration
 * dance, PING/PONG, nick-collision fallback, and reconnect-with-backoff,
 * and exposes verb methods ({@link #join}, {@link #privmsg}, …) that
 * render proper wire lines.
 *
 * <p><b>Threading contract (read this before wiring a listener):</b>
 * every socket byte moves on this client's own
 * {@link RequestProcessor}{@code ("IRC", 2)} — one slot runs the reader
 * loop for the life of the connection, the other serializes writes and
 * reconnect timers. NOTHING here touches the EDT, and every
 * {@link Listener} callback is invoked <b>on the engine thread</b>: a
 * UI listener must marshal to the EDT itself
 * ({@code SwingUtilities.invokeLater}) before touching a component.
 * That split keeps a stalled network from ever freezing a repaint.
 *
 * <p><b>Bounded reads:</b> a hostile or broken server line is capped at
 * {@link #MAX_LINE_CHARS} chars — past the cap the line is truncated
 * with an honest marker and the rest is drained-and-discarded while the
 * connection survives (the house {@code readLineBounded} law, same as
 * the rack's process pump).
 *
 * <p><b>State machine:</b> {@code CLOSED → CONNECTING → REGISTERING →
 * READY}, with {@code RECONNECTING} between drops (capped exponential
 * backoff, 2s doubling to 60s, reset on a successful registration) and
 * {@code CLOSED} only via {@link #quitAndClose} or an exhausted nick
 * retry. After a reconnect registers, previously-joined channels are
 * rejoined automatically.
 *
 * <p><b>Secrets:</b> when {@link IrcSecrets} holds a NickServ password
 * for this network, one {@code PRIVMSG NickServ :IDENTIFY …} is sent
 * right after the 001 welcome. The password is read from the OS
 * keychain at that moment, never stored on this object, never logged,
 * and never echoed to listeners (outgoing lines don't fire
 * {@link Listener#lineReceived}).
 *
 * <p><b>IRCv3 (v1.205.0):</b> registration opens with {@code CAP LS
 * 302} and requests {@link #SUPPORTED_CAPS} ∩ offered; when the profile
 * names a SASL account and the keychain holds a password, SASL PLAIN
 * runs BEFORE {@code CAP END} (so a services-gated network sees an
 * authenticated registration), with 904-style failures surfacing as
 * honest lines and never a password retry loop. A pre-IRCv3 server
 * simply ignores the CAP line and its 001 clears the negotiation state.
 */
public final class IrcClient {

    private static final Logger LOG = Logger.getLogger(IrcClient.class.getName());

    /**
     * Per-line ceiling. The RFC caps a line at 512 bytes but the wild
     * grows them bigger (IRCv3 tags, sloppy bridges); 8192 chars is far
     * beyond any honest line and keeps a malicious no-newline flood from
     * growing one String until OOM.
     */
    static final int MAX_LINE_CHARS = 8192;

    /** Appended to a line that hit the ceiling, so the transcript is honest. */
    static final String TRUNCATION_MARKER = " …[line truncated]";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int MAX_NICK_RETRIES = 3;

    /** Where the connection honestly is; see the class javadoc's machine. */
    public enum State {
        /** Socket dialing (or TLS handshaking). */
        CONNECTING,
        /** Socket up; NICK/USER sent, waiting for 001. */
        REGISTERING,
        /** Registered and usable. */
        READY,
        /** Dropped; a backoff timer will redial. */
        RECONNECTING,
        /** Gone for good (quit, or nick retries exhausted). */
        CLOSED
    }

    /**
     * Engine events. <b>Every method is called on the engine thread</b> —
     * marshal to the EDT before touching Swing (see class javadoc).
     */
    public interface Listener {

        /** The socket is up; registration is starting. */
        void connected();

        /** 001 arrived; {@code nick} is the name the server accepted. */
        void registered(String nick);

        /** Every parsed inbound line, after the engine's own handling. */
        void lineReceived(IrcMessage message);

        /** The connection dropped (or was closed); {@code reason} is honest prose. */
        void disconnected(String reason);
    }

    /**
     * The immutable dial plan for one network.
     *
     * @param network        the saved-network name ({@link IrcSecrets} key)
     * @param host           server hostname
     * @param port           server port
     * @param tls            TLS ({@link SSLSocketFactory}) vs plaintext
     * @param nick           nickname to register
     * @param realName       the USER real-name field; null falls back to nick
     * @param serverPassword optional PASS value, or null; callers should
     *                       source one from a keychain, never from prefs
     * @param saslAccount    SASL PLAIN account name, or null/empty for
     *                       none (the NickServ-after-001 fallback runs
     *                       instead); the password is read from
     *                       {@link IrcSecrets} at authenticate time
     */
    public record Profile(String network, String host, int port, boolean tls,
            String nick, String realName, String serverPassword, String saslAccount) {

        /** The common shape: no server password, no SASL, real name = nick. */
        public Profile(String network, String host, int port, boolean tls, String nick) {
            this(network, host, port, tls, nick, null, null, null);
        }
    }

    /**
     * The IRCv3 capabilities this client understands and will request
     * when the server offers them. {@code sasl} is requested only when
     * the profile carries a SASL account AND the keychain a password.
     */
    static final Set<String> SUPPORTED_CAPS = Set.of(
            "sasl", "server-time", "message-tags", "multi-prefix",
            "away-notify", "account-notify", "echo-message");

    private final Profile profile;
    private final RequestProcessor rp;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    /** lowercase channel → original-case channel, for auto-rejoin. */
    private final Map<String, String> joinedChannels = new ConcurrentHashMap<>();

    /** Caps the server ACKed this session; cleared on every (re)dial. */
    private final Set<String> enabledCaps = ConcurrentHashMap.newKeySet();
    /** Caps the server's CAP LS offered (engine thread only). */
    private final Set<String> offeredCaps = new java.util.HashSet<>();
    /** True from CAP LS until CAP END/001 (engine thread only). */
    private boolean capNegotiating;

    /** Lowercased nicks whose PRIVMSG/NOTICE are dropped before listeners. */
    private volatile Set<String> ignoredNicks = Set.of();

    private final Object writeLock = new Object();
    private BufferedWriter out; // guarded by writeLock

    private final Object stateLock = new Object();
    private volatile State state = State.CLOSED;

    private volatile Socket socket;
    private volatile String currentNick;
    private volatile boolean closed = true;
    private volatile RequestProcessor.Task readerTask;
    private volatile RequestProcessor.Task pendingReconnect;
    private int nickAttempts; // engine (reader) thread only

    /** Test seams: backoff floor/ceiling, package-private on purpose. */
    volatile long baseBackoffMs = 2_000;
    volatile long maxBackoffMs = 60_000;
    private volatile long backoffMs = baseBackoffMs;

    public IrcClient(Profile profile) {
        this.profile = profile;
        this.currentNick = profile.nick();
        this.rp = new RequestProcessor("IRC " + profile.network(), 2);
    }

    /** The dial plan this client was built with. */
    public Profile profile() {
        return profile;
    }

    /** Where the connection honestly is right now. */
    public State state() {
        return state;
    }

    /** The nick the server currently knows us by (fallbacks included). */
    public String currentNick() {
        return currentNick;
    }

    /** Channels we consider ourselves in (used for auto-rejoin). */
    public List<String> joinedChannels() {
        return new ArrayList<>(joinedChannels.values());
    }

    /**
     * True when the server ACKed the named IRCv3 capability this
     * session. The UI keys behavior off this — e.g. with
     * {@code echo-message} active it skips the local echo of a sent
     * PRIVMSG, because the server will echo it back.
     */
    public boolean capEnabled(String cap) {
        return enabledCaps.contains(cap);
    }

    /**
     * Replaces the ignore set: PRIVMSG/NOTICE (including CTCP) from
     * these nicks are dropped BEFORE listeners fire — no transcript, no
     * log, no auto-reply. JOIN/PART/QUIT still flow (presence isn't
     * speech). Case-insensitive; callable from any thread.
     */
    public void setIgnoredNicks(java.util.Collection<String> nicks) {
        Set<String> lower = new java.util.HashSet<>();
        for (String n : nicks) {
            lower.add(n.toLowerCase(Locale.ROOT));
        }
        this.ignoredNicks = Set.copyOf(lower);
    }

    /**
     * Registers a listener; adding one twice delivers once (the house
     * equality-guarded-add law, so a window reopen can't double-render).
     */
    public void addListener(Listener l) {
        listeners.addIfAbsent(l);
    }

    /** Detaches a listener (window close symmetry). */
    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /**
     * Dials the server. A no-op unless the client is {@code CLOSED} —
     * reconnects are the engine's own job. Returns immediately; all
     * socket work happens on the engine RP.
     */
    public synchronized void connect() {
        if (state != State.CLOSED) {
            return;
        }
        closed = false;
        backoffMs = baseBackoffMs;
        setState(State.CONNECTING);
        readerTask = rp.post(this::runSession);
    }

    /**
     * Sends {@code QUIT} and tears the connection down for good: no
     * reconnect, state {@code CLOSED}, socket closed, and the reader
     * task ends (no thread leak — the RP's threads idle out).
     */
    public void quitAndClose(String message) {
        closed = true;
        RequestProcessor.Task pending = pendingReconnect;
        if (pending != null) {
            pending.cancel();
        }
        rp.post(() -> {
            sendNow("QUIT :" + (message == null || message.isEmpty() ? "bye" : message));
            Socket s = socket;
            if (s != null) {
                closeQuietly(s); // unblocks the reader, which announces CLOSED
            } else {
                toClosed("closed");
            }
        });
    }

    // ---- verbs -----------------------------------------------------------

    /** Joins a channel (and remembers it for auto-rejoin). */
    public void join(String channel) {
        joinedChannels.put(lower(channel), channel);
        send("JOIN " + channel);
    }

    /** Parts a channel (and forgets it for auto-rejoin). */
    public void part(String channel) {
        joinedChannels.remove(lower(channel));
        send("PART " + channel);
    }

    /** Says {@code text} to a channel or nick, splitting long text honestly. */
    public void privmsg(String target, String text) {
        for (String piece : splitForWire("PRIVMSG " + target + " :", text)) {
            send("PRIVMSG " + target + " :" + piece);
        }
    }

    /** Sends a NOTICE to a channel or nick, splitting long text honestly. */
    public void notice(String target, String text) {
        for (String piece : splitForWire("NOTICE " + target + " :", text)) {
            send("NOTICE " + target + " :" + piece);
        }
    }

    /**
     * RFC 1459 caps a wire line at 512 bytes including the command
     * prefix and CRLF. A long paste sent as one line would be silently
     * truncated by the SERVER — while the local echo showed text the
     * channel never received. Splitting here keeps the transcript
     * truthful: what you see locally is what everyone got. Cuts land on
     * UTF-8 code-point boundaries (never mid-surrogate) and prefer the
     * last space in the tail of each piece so words survive.
     */
    static List<String> splitForWire(String prefix, String text) {
        int budget = 510 - prefix.getBytes(StandardCharsets.UTF_8).length;
        if (budget < 1) {
            budget = 1; // pathological target name; still make progress
        }
        List<String> pieces = new ArrayList<>();
        String rest = text;
        while (rest.getBytes(StandardCharsets.UTF_8).length > budget) {
            // widest prefix of rest that fits the byte budget, cut on a
            // code-point boundary
            int end = 0;
            int bytes = 0;
            while (end < rest.length()) {
                int cp = rest.codePointAt(end);
                int cpBytes = new String(Character.toChars(cp))
                        .getBytes(StandardCharsets.UTF_8).length;
                if (bytes + cpBytes > budget) {
                    break;
                }
                bytes += cpBytes;
                end += Character.charCount(cp);
            }
            // prefer a word boundary in the last quarter of the piece
            int space = rest.lastIndexOf(' ', end - 1);
            int cut = (space > end - Math.max(1, end / 4)) ? space : end;
            pieces.add(rest.substring(0, cut));
            rest = rest.substring(cut == space ? cut + 1 : cut);
        }
        pieces.add(rest);
        return pieces;
    }

    /** Asks the server for a new nickname. */
    public void nick(String newNick) {
        send("NICK " + newNick);
    }

    /** Sets ({@code text != null}) or queries a channel's topic. */
    public void topic(String channel, String text) {
        send(text == null ? "TOPIC " + channel : "TOPIC " + channel + " :" + text);
    }

    /** Asks who a nick is. */
    public void whois(String nickname) {
        send("WHOIS " + nickname);
    }

    /** Sends a raw line verbatim ({@code /raw}). */
    public void sendRaw(String line) {
        send(line);
    }

    // ---- session ---------------------------------------------------------

    private void runSession() {
        // A quit that lands while the reconnect task sat between its own
        // closed-check and this post must not resurrect the connection:
        // the user was told CLOSED, and a zombie session would identify
        // to NickServ invisibly.
        if (closed) {
            return;
        }
        Socket s = null;
        String failure = null;
        try {
            SocketFactory factory = profile.tls()
                    ? SSLSocketFactory.getDefault()
                    : SocketFactory.getDefault();
            s = factory.createSocket();
            if (s instanceof SSLSocket ssl) {
                // A raw SSLSocket validates the chain but NOT the hostname
                // (unlike HttpsURLConnection) — without this, any CA-valid
                // certificate for any domain passes, and an on-path
                // attacker receives the SASL/NickServ credentials sent
                // right after registration.
                SSLParameters sp = ssl.getSSLParameters();
                sp.setEndpointIdentificationAlgorithm("HTTPS");
                ssl.setSSLParameters(sp);
            }
            s.connect(new InetSocketAddress(profile.host(), profile.port()), CONNECT_TIMEOUT_MS);
            if (closed) {
                closeQuietly(s);
                return;
            }
            socket = s;
            BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            synchronized (writeLock) {
                out = w;
            }
            fireConnected();
            setState(State.REGISTERING);
            nickAttempts = 0;
            currentNick = profile.nick();
            register();

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = readLineBounded(in, MAX_LINE_CHARS)) != null) {
                if (!line.isEmpty()) {
                    handleLine(line);
                }
            }
        } catch (IOException ex) {
            failure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        } finally {
            synchronized (writeLock) {
                out = null;
            }
            closeQuietly(s);
            socket = null;
            endSession(failure);
        }
    }

    private void register() {
        // IRCv3 first: a capable server holds 001 until CAP END; a
        // pre-CAP server silently ignores this line and sends 001, which
        // clears the negotiation state (honest degradation, no timeout
        // machinery needed)
        capNegotiating = true;
        offeredCaps.clear();
        enabledCaps.clear();
        sendNow("CAP LS 302");
        String pass = profile.serverPassword();
        if (pass != null && !pass.isEmpty()) {
            sendNow("PASS " + pass);
        }
        sendNow("NICK " + currentNick);
        sendNow("USER " + userName(profile.nick()) + " 0 * :"
                + (profile.realName() == null ? profile.nick() : profile.realName()));
    }

    // ---- IRCv3 capability negotiation + SASL PLAIN -----------------------

    /** True when the profile names a SASL account and the keychain has a password. */
    private boolean wantsSasl() {
        return profile.saslAccount() != null && !profile.saslAccount().isEmpty()
                && !IrcSecrets.read(profile.network()).isEmpty();
    }

    /**
     * One {@code CAP} subcommand from the server (engine thread).
     * LS may span lines ({@code CAP * LS * :…} continuations); the REQ
     * goes out when the last LS line lands, asking for exactly
     * {@link #SUPPORTED_CAPS} ∩ offered (sasl only when usable).
     */
    private void handleCap(IrcMessage msg) {
        String sub = msg.param(1);
        String blob = msg.trailing() == null ? "" : msg.trailing();
        switch (sub) {
            case "LS" -> {
                for (String token : blob.split(" ")) {
                    if (token.isEmpty()) {
                        continue;
                    }
                    int eq = token.indexOf('='); // sasl=PLAIN,EXTERNAL → sasl
                    offeredCaps.add(eq < 0 ? token : token.substring(0, eq));
                }
                boolean moreComing = "*".equals(msg.param(2)) && msg.params().size() > 3;
                if (moreComing || !capNegotiating) {
                    return;
                }
                List<String> want = new ArrayList<>();
                for (String cap : SUPPORTED_CAPS) {
                    if (!offeredCaps.contains(cap)) {
                        continue;
                    }
                    if ("sasl".equals(cap) && !wantsSasl()) {
                        continue; // requesting sasl we won't use just stalls registration
                    }
                    want.add(cap);
                }
                if (want.isEmpty()) {
                    capEnd();
                } else {
                    want.sort(null); // deterministic wire order (tests pin it)
                    sendNow("CAP REQ :" + String.join(" ", want));
                }
            }
            case "ACK" -> {
                for (String token : blob.split(" ")) {
                    if (token.isEmpty()) {
                        continue;
                    }
                    if (token.startsWith("-")) {
                        enabledCaps.remove(token.substring(1));
                    } else {
                        enabledCaps.add(token);
                    }
                }
                if (capNegotiating) {
                    if (enabledCaps.contains("sasl") && wantsSasl()) {
                        sendNow("AUTHENTICATE PLAIN"); // 903/904-907 end negotiation
                    } else {
                        capEnd();
                    }
                }
            }
            case "NAK" ->
                capEnd(); // the server refused the set; register without extras
            case "DEL" -> {
                for (String token : blob.split(" ")) {
                    enabledCaps.remove(token);
                }
            }
            default -> {
                // NEW and friends: nothing to do mid-session
            }
        }
    }

    /**
     * The server's {@code AUTHENTICATE +} go-ahead: read the password
     * from the keychain NOW (never held on a field), send the PLAIN
     * payload in spec-sized chunks, and let the credential go.
     */
    private void handleAuthenticate(IrcMessage msg) {
        if (!"+".equals(msg.param(0)) && !"+".equals(msg.trailing())) {
            return; // not the empty-challenge go-ahead PLAIN expects
        }
        String password = IrcSecrets.read(profile.network());
        for (String chunk : SaslPlain.chunks(profile.saslAccount(), password)) {
            sendNow("AUTHENTICATE " + chunk);
        }
    }

    /** Ends capability negotiation exactly once; registration resumes. */
    private void capEnd() {
        if (capNegotiating) {
            capNegotiating = false;
            sendNow("CAP END");
        }
    }

    /** The USER field must be a simple word; derive one from the nick. */
    private static String userName(String nick) {
        StringBuilder sb = new StringBuilder(nick.length());
        for (int i = 0; i < nick.length(); i++) {
            char c = nick.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? "nmox" : sb.toString();
    }

    private void handleLine(String line) {
        IrcMessage msg;
        try {
            msg = IrcMessage.parse(line);
        } catch (IllegalArgumentException ex) {
            LOG.log(Level.FINE, "unparseable IRC line dropped: {0}", ex.getMessage());
            return;
        }
        // the ignore law: an ignored nick's speech (and only speech —
        // presence still flows) is dropped before ANY listener, log, or
        // auto-reply sees it
        if (("PRIVMSG".equals(msg.command()) || "NOTICE".equals(msg.command()))
                && msg.nick() != null
                && ignoredNicks.contains(msg.nick().toLowerCase(Locale.ROOT))) {
            return;
        }
        // The echo-message cap bounces our own PRIVMSGs back as inbound
        // lines. For a service target that means the NickServ IDENTIFY
        // password would render into the on-screen transcript — the one
        // gap in the "credentials never echo" promise (the log side is
        // already suppressed by IrcLogger.isService). Drop the self-echo
        // of service traffic before any listener sees it.
        if ("PRIVMSG".equals(msg.command())
                && msg.nick() != null
                && msg.nick().equalsIgnoreCase(currentNick)
                && IrcLogger.isService(msg.param(0))) {
            return;
        }
        switch (msg.command()) {
            case "PING" ->
                sendNow("PONG :" + msg.param(0));
            case "CAP" ->
                handleCap(msg);
            case "AUTHENTICATE" ->
                handleAuthenticate(msg);
            case "903" ->
                capEnd(); // SASL success; registration resumes
            case "904", "905", "906", "907" ->
                // SASL failure: honest line reaches the UI via the fan-out
                // below; registration continues WITHOUT retrying the
                // password (a retry loop against a wrong credential is a
                // lockout machine)
                capEnd();
            case "001" -> {
                capNegotiating = false; // a pre-CAP server never answered CAP LS
                currentNick = msg.param(0).isEmpty() ? currentNick : msg.param(0);
                backoffMs = baseBackoffMs; // a good registration resets the clock
                setState(State.READY);
                fireRegistered(currentNick);
                identifyWithNickServ();
                rejoinChannels();
            }
            case "433" -> {
                if (state == State.REGISTERING) {
                    nickAttempts++;
                    if (nickAttempts >= MAX_NICK_RETRIES) {
                        LOG.log(Level.INFO, "nickname retries exhausted on {0}", profile.network());
                        closed = true;
                        closeQuietly(socket); // reader loop ends -> CLOSED
                    } else {
                        currentNick = currentNick + "_";
                        sendNow("NICK " + currentNick);
                    }
                }
            }
            case "NICK" -> {
                if (msg.nick() != null && msg.nick().equalsIgnoreCase(currentNick)) {
                    String newNick = msg.trailing() != null ? msg.trailing() : msg.param(0);
                    if (!newNick.isEmpty()) {
                        currentNick = newNick;
                    }
                }
            }
            case "JOIN" -> {
                if (isSelf(msg)) {
                    String chan = msg.trailing() != null ? msg.trailing() : msg.param(0);
                    if (!chan.isEmpty()) {
                        joinedChannels.put(lower(chan), chan);
                    }
                }
            }
            case "PART" -> {
                if (isSelf(msg)) {
                    joinedChannels.remove(lower(msg.param(0)));
                }
            }
            case "KICK" -> {
                if (msg.param(1).equalsIgnoreCase(currentNick)) {
                    joinedChannels.remove(lower(msg.param(0)));
                }
            }
            case "PRIVMSG" ->
                maybeAnswerCtcp(msg);
            default -> {
                // routed by kind in the UI; nothing engine-side
            }
        }
        fireLineReceived(msg);
    }

    /** Answers CTCP VERSION and PING queries; ACTION is the UI's to render. */
    private void maybeAnswerCtcp(IrcMessage msg) {
        String body = msg.trailing();
        Ctcp ctcp = body == null ? null : Ctcp.extract(body);
        if (ctcp == null || msg.nick() == null) {
            return;
        }
        switch (ctcp.command()) {
            case Ctcp.VERSION ->
                sendNow("NOTICE " + msg.nick() + " :" + Ctcp.wrap(Ctcp.VERSION, "NMOX Studio IRC"));
            case Ctcp.PING ->
                sendNow("NOTICE " + msg.nick() + " :" + Ctcp.wrap(Ctcp.PING, ctcp.argument()));
            default -> {
                // ACTION and the rest: presentation, not protocol
            }
        }
    }

    /**
     * The one place a NickServ password is used: read from the keychain
     * (or its in-memory fallback) at identify time, sent, and let go.
     * Never logged, never kept on a field. Runs only for profiles
     * WITHOUT a SASL account — with one, the same keychain entry was
     * already spent on AUTHENTICATE before registration completed, and
     * identifying twice would just wake NickServ for nothing.
     */
    private void identifyWithNickServ() {
        if (profile.saslAccount() != null && !profile.saslAccount().isEmpty()) {
            return;
        }
        String password = IrcSecrets.read(profile.network());
        if (!password.isEmpty()) {
            sendNow("PRIVMSG NickServ :IDENTIFY " + password);
        }
    }

    private void rejoinChannels() {
        for (String channel : joinedChannels.values()) {
            sendNow("JOIN " + channel);
        }
    }

    private boolean isSelf(IrcMessage msg) {
        return msg.nick() != null && msg.nick().equalsIgnoreCase(currentNick);
    }

    private void endSession(String failure) {
        if (closed) {
            toClosed(failure == null ? "closed" : failure);
            return;
        }
        setState(State.RECONNECTING);
        fireDisconnected(failure == null ? "connection lost" : failure);
        long delay = backoffMs;
        backoffMs = Math.min(maxBackoffMs, backoffMs * 2);
        pendingReconnect = rp.post(() -> {
            if (closed) {
                return;
            }
            setState(State.CONNECTING);
            readerTask = rp.post(this::runSession);
        }, (int) Math.min(delay, Integer.MAX_VALUE));
    }

    /** CLOSED is announced exactly once per close, even if quit races the reader. */
    private void toClosed(String reason) {
        boolean announce;
        synchronized (stateLock) {
            announce = state != State.CLOSED;
            state = State.CLOSED;
        }
        if (announce) {
            fireDisconnected(reason);
        }
    }

    private void setState(State s) {
        synchronized (stateLock) {
            state = s;
        }
    }

    // ---- IO plumbing -----------------------------------------------------

    /** Queues a line onto the engine RP's write slot (callable from any thread). */
    private void send(String line) {
        // Defense-in-depth: an embedded CR/LF would smuggle a second raw
        // protocol line past every verb. No live caller can produce one
        // today (JTextField filters pasted newlines), but the engine is
        // the last line of defense, so it enforces the invariant itself.
        String flat = line.indexOf('\r') < 0 && line.indexOf('\n') < 0
                ? line
                : line.replace("\r", " ").replace("\n", " ");
        rp.post(() -> sendNow(flat));
    }

    /** Writes a line right now (engine thread); a dead pipe is the reader's news. */
    private void sendNow(String line) {
        synchronized (writeLock) {
            if (out == null) {
                return;
            }
            try {
                out.write(line);
                out.write("\r\n");
                out.flush();
            } catch (IOException ex) {
                LOG.log(Level.FINE, "IRC write failed (reader will reconnect)", ex);
            }
        }
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // closing is best-effort
            }
        }
    }

    private static String lower(String s) {
        return s.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * {@code readLine} with a ceiling: same terminator handling
     * ({@code \n}, {@code \r}, {@code \r\n}), but a line exceeding
     * {@code max} chars comes back truncated with
     * {@link #TRUNCATION_MARKER} and the rest of that physical line is
     * drained and discarded — the server keeps writing into a moving
     * pipe (no deadlock) while our memory stays bounded. Package-private
     * for the flood test.
     */
    static String readLineBounded(BufferedReader reader, int max) throws IOException {
        StringBuilder sb = new StringBuilder(160);
        int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') {
                return sb.toString();
            }
            if (c == '\r') {
                // swallow the \n of a \r\n pair without eating a lone \r's successor
                reader.mark(1);
                int next = reader.read();
                if (next != '\n' && next != -1) {
                    reader.reset();
                }
                return sb.toString();
            }
            if (sb.length() >= max) {
                // ceiling hit: discard the rest of this physical line
                while ((c = reader.read()) != -1 && c != '\n') {
                    if (c == '\r') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next != '\n' && next != -1) {
                            reader.reset();
                        }
                        break;
                    }
                }
                // Never cut mid-surrogate-pair: an emoji at the cap must
                // truncate to a whole code point, not a lone surrogate
                // (the v1.149.0 code-point-safe-caps law).
                if (Character.isHighSurrogate(sb.charAt(sb.length() - 1))) {
                    sb.setLength(sb.length() - 1);
                }
                return sb.append(TRUNCATION_MARKER).toString();
            }
            sb.append((char) c);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    // ---- listener fan-out (engine thread; one bad listener never kills the pump)

    private void fireConnected() {
        for (Listener l : listeners) {
            try {
                l.connected();
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "IRC listener failed in connected()", ex);
            }
        }
    }

    private void fireRegistered(String nick) {
        for (Listener l : listeners) {
            try {
                l.registered(nick);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "IRC listener failed in registered()", ex);
            }
        }
    }

    private void fireLineReceived(IrcMessage msg) {
        for (Listener l : listeners) {
            try {
                l.lineReceived(msg);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "IRC listener failed in lineReceived()", ex);
            }
        }
    }

    private void fireDisconnected(String reason) {
        for (Listener l : listeners) {
            try {
                l.disconnected(reason);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "IRC listener failed in disconnected()", ex);
            }
        }
    }

    // ---- test support ----------------------------------------------------

    /** Polls until the state matches or the deadline passes (tests). */
    public boolean awaitState(State expected, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (state == expected) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return state == expected;
    }

    /** True when the reader task has ended (the no-thread-leak assertion). */
    boolean awaitReaderFinished(long timeoutMs) throws InterruptedException {
        RequestProcessor.Task t = readerTask;
        return t == null || t.waitFinished(timeoutMs);
    }
}

package org.nmox.studio.ui.irc.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An in-JVM fake IRC server on a loopback {@link ServerSocket}: it
 * accepts connections (repeatedly — the reconnect tests need a second
 * accept), queues every line the client sends, and lets a test script
 * the server side ({@link #send}) line by line. Plaintext on purpose:
 * TLS would only test the JDK, and the client's plaintext mode is the
 * seam the engine tests ride.
 */
final class FakeIrcServer implements AutoCloseable {

    private final ServerSocket server;
    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    private final AtomicInteger accepts = new AtomicInteger();
    private volatile Socket client;
    private volatile BufferedWriter out;
    private volatile boolean closed;

    FakeIrcServer() throws IOException {
        server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::acceptLoop, "fake-irc-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int port() {
        return server.getLocalPort();
    }

    int acceptCount() {
        return accepts.get();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket s = server.accept();
                accepts.incrementAndGet();
                client = s;
                out = new BufferedWriter(
                        new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
                Thread reader = new Thread(() -> readLoop(s), "fake-irc-reader");
                reader.setDaemon(true);
                reader.start();
            } catch (IOException ex) {
                return; // server socket closed: test over
            }
        }
    }

    private void readLoop(Socket s) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                received.add(line);
            }
        } catch (IOException ignored) {
            // client dropped: fine
        }
    }

    /** Sends one server line (CRLF appended) to the CURRENT client. */
    void send(String line) throws IOException {
        BufferedWriter w = out;
        if (w == null) {
            throw new IOException("no client connected");
        }
        synchronized (this) {
            w.write(line);
            w.write("\r\n");
            w.flush();
        }
    }

    /**
     * Waits for the next client line starting with {@code prefix},
     * discarding non-matching lines (tests are sequential scripts).
     * Fails loudly on timeout so a hang has a name.
     */
    String awaitLine(String prefix, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (true) {
            long left = (deadline - System.nanoTime()) / 1_000_000L;
            if (left <= 0) {
                throw new AssertionError("timed out waiting for a line starting with: " + prefix);
            }
            String line = received.poll(left, TimeUnit.MILLISECONDS);
            if (line == null) {
                throw new AssertionError("timed out waiting for a line starting with: " + prefix);
            }
            if (line.startsWith(prefix)) {
                return line;
            }
        }
    }

    /** True when a line starting with {@code prefix} arrives before the deadline. */
    boolean sawLine(String prefix, long timeoutMs) {
        try {
            awaitLine(prefix, timeoutMs);
            return true;
        } catch (AssertionError | InterruptedException ex) {
            return false;
        }
    }

    /** Drops the current client connection (the reconnect trigger). */
    void dropClient() throws IOException {
        Socket s = client;
        if (s != null) {
            // shutdownOutput FIRST so the client sees an orderly FIN.
            // A bare close() with bytes still unread makes Windows send
            // RST instead, and the client's blocked reader can miss the
            // clean EOF that drives its redial — the v1.205.0 Windows-
            // lane flake (green on ubuntu/macOS, red once on Windows).
            try {
                s.shutdownOutput();
            } catch (IOException ignored) {
                // already half-closed: the close below still ends it
            }
            s.close();
        }
    }

    /** Standard registration script: expect NICK+USER, answer 001. */
    void completeRegistration(String acceptedNick, long timeoutMs)
            throws IOException, InterruptedException {
        awaitLine("NICK ", timeoutMs);
        awaitLine("USER ", timeoutMs);
        send(":fake.server 001 " + acceptedNick + " :Welcome to the fake network");
    }

    /**
     * IRCv3 registration script: expect {@code CAP LS 302} + NICK +
     * USER, offer {@code offeredCaps}, ACK whatever the client REQs
     * (returned so the test can pin the exact set), wait for
     * {@code CAP END}, then 001. For SASL flows use the finer-grained
     * {@link #awaitLine}/{@link #send} directly between ACK and END.
     */
    String completeCapRegistration(String acceptedNick, String offeredCaps, long timeoutMs)
            throws IOException, InterruptedException {
        awaitLine("CAP LS", timeoutMs);
        awaitLine("NICK ", timeoutMs);
        awaitLine("USER ", timeoutMs);
        send(":fake.server CAP * LS :" + offeredCaps);
        String req = awaitLine("CAP REQ :", timeoutMs);
        String requested = req.substring("CAP REQ :".length());
        send(":fake.server CAP " + acceptedNick + " ACK :" + requested);
        return requested;
    }

    /** After an ACK containing sasl: run the PLAIN dance, return the payload. */
    String completeSaslPlain(String acceptedNick, long timeoutMs)
            throws IOException, InterruptedException {
        awaitLine("AUTHENTICATE PLAIN", timeoutMs);
        send("AUTHENTICATE +");
        String payload = awaitLine("AUTHENTICATE ", timeoutMs)
                .substring("AUTHENTICATE ".length());
        send(":fake.server 903 " + acceptedNick + " :SASL authentication successful");
        return payload;
    }

    /** The registration tail every CAP script ends with: CAP END then 001. */
    void finishCapRegistration(String acceptedNick, long timeoutMs)
            throws IOException, InterruptedException {
        awaitLine("CAP END", timeoutMs);
        send(":fake.server 001 " + acceptedNick + " :Welcome to the fake network");
    }

    @Override
    public void close() throws IOException {
        closed = true;
        dropClient();
        server.close();
    }
}

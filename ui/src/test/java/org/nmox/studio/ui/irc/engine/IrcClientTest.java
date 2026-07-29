package org.nmox.studio.ui.irc.engine;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.irc.protocol.IrcMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine against a real (loopback, in-JVM) IRC server: the full
 * registration handshake, PING/PONG, the 433 underscore fallback,
 * bounded line reads surviving a 100k-char flood, reconnect-and-rejoin,
 * NickServ identify from the secrets store's in-memory fallback, and a
 * clean QUIT with no reader-thread leak. Plaintext mode is the test
 * seam; the TLS path only swaps the socket factory.
 */
class IrcClientTest {

    private static final long T = 8_000; // generous per-step timeout; CI runs loaded

    private FakeIrcServer server;
    private IrcClient client;
    private RecordingListener listener;
    private boolean originalKeyring;

    /** Captures engine callbacks; asserts ride its queues. */
    static final class RecordingListener implements IrcClient.Listener {

        final List<String> events = new CopyOnWriteArrayList<>();
        final BlockingQueue<IrcMessage> lines = new LinkedBlockingQueue<>();

        @Override
        public void connected() {
            events.add("connected");
        }

        @Override
        public void registered(String nick) {
            events.add("registered:" + nick);
        }

        @Override
        public void lineReceived(IrcMessage message) {
            lines.add(message);
        }

        @Override
        public void disconnected(String reason) {
            events.add("disconnected");
        }

        IrcMessage awaitCommand(String command, long timeoutMs) throws InterruptedException {
            long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
            while (true) {
                long left = (deadline - System.nanoTime()) / 1_000_000L;
                if (left <= 0) {
                    throw new AssertionError("no " + command + " delivered in time");
                }
                IrcMessage m = lines.poll(left, TimeUnit.MILLISECONDS);
                if (m == null) {
                    throw new AssertionError("no " + command + " delivered in time");
                }
                if (m.command().equals(command)) {
                    return m;
                }
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        originalKeyring = IrcSecrets.keyringUsable;
        IrcSecrets.keyringUsable = false; // never touch the OS keychain in tests
        server = new FakeIrcServer();
        listener = new RecordingListener();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.quitAndClose("test over");
            client.awaitState(IrcClient.State.CLOSED, T);
        }
        server.close();
        IrcSecrets.delete("testnet");
        IrcSecrets.keyringUsable = originalKeyring;
    }

    private IrcClient newClient(String nick) {
        IrcClient c = new IrcClient(new IrcClient.Profile(
                "testnet", "127.0.0.1", server.port(), false, nick));
        c.baseBackoffMs = 50; // tests must not wait out real backoff
        c.addListener(listener);
        return c;
    }

    @Test
    @DisplayName("Registration: NICK+USER go out, 001 lands READY with the registered callback")
    void registrationHandshake() throws Exception {
        client = newClient("nmox-user");
        client.connect();
        String nickLine = server.awaitLine("NICK ", T);
        assertThat(nickLine).isEqualTo("NICK nmox-user");
        String userLine = server.awaitLine("USER ", T);
        assertThat(userLine).startsWith("USER nmox-user 0 * :");
        server.send(":fake.server 001 nmox-user :Welcome");
        assertThat(client.awaitState(IrcClient.State.READY, T)).isTrue();
        assertThat(listener.events).contains("connected", "registered:nmox-user");
        assertThat(client.currentNick()).isEqualTo("nmox-user");
    }

    @Test
    @DisplayName("A server PING is answered with PONG, automatically")
    void pingPong() throws Exception {
        client = newClient("nmox-user");
        client.connect();
        server.completeRegistration("nmox-user", T);
        client.awaitState(IrcClient.State.READY, T);
        server.send("PING :abc123");
        assertThat(server.awaitLine("PONG ", T)).isEqualTo("PONG :abc123");
    }

    @Test
    @DisplayName("JOIN goes out; the echo plus 353/366 names reach the listener")
    void joinAndNames() throws Exception {
        client = newClient("nmox-user");
        client.connect();
        server.completeRegistration("nmox-user", T);
        client.awaitState(IrcClient.State.READY, T);

        client.join("#test");
        assertThat(server.awaitLine("JOIN ", T)).isEqualTo("JOIN #test");
        server.send(":nmox-user!u@h JOIN #test");
        server.send(":fake.server 353 nmox-user = #test :@oper +voiced nmox-user");
        server.send(":fake.server 366 nmox-user #test :End of /NAMES list");

        IrcMessage names = listener.awaitCommand("353", T);
        assertThat(names.trailing()).isEqualTo("@oper +voiced nmox-user");
        listener.awaitCommand("366", T);
        assertThat(client.joinedChannels()).containsExactly("#test");
    }

    @Test
    @DisplayName("433 retries with an underscore, capped, and registers under the fallback")
    void nickInUseFallsBack() throws Exception {
        client = newClient("nmox-user");
        client.connect();
        server.awaitLine("NICK nmox-user", T);
        server.awaitLine("USER ", T);
        server.send(":fake.server 433 * nmox-user :Nickname is already in use");
        assertThat(server.awaitLine("NICK ", T)).isEqualTo("NICK nmox-user_");
        server.send(":fake.server 433 * nmox-user_ :Nickname is already in use");
        assertThat(server.awaitLine("NICK ", T)).isEqualTo("NICK nmox-user__");
        server.send(":fake.server 001 nmox-user__ :Welcome at last");
        assertThat(client.awaitState(IrcClient.State.READY, T)).isTrue();
        assertThat(client.currentNick()).isEqualTo("nmox-user__");
    }

    @Test
    @DisplayName("A 100k-char line is truncated with the marker and the connection survives")
    void floodedLineIsBoundedAndSurvives() throws Exception {
        client = newClient("nmox-user");
        client.connect();
        server.completeRegistration("nmox-user", T);
        client.awaitState(IrcClient.State.READY, T);

        StringBuilder flood = new StringBuilder(":x!y@z PRIVMSG nmox-user :");
        flood.append("a".repeat(100_000));
        server.send(flood.toString());
        server.send(":x!y@z PRIVMSG nmox-user :after the flood");

        IrcMessage big = listener.awaitCommand("PRIVMSG", T);
        assertThat(big.trailing())
                .hasSizeLessThan(IrcClient.MAX_LINE_CHARS + IrcClient.TRUNCATION_MARKER.length() + 1)
                .endsWith(IrcClient.TRUNCATION_MARKER);
        IrcMessage after = listener.awaitCommand("PRIVMSG", T);
        assertThat(after.trailing()).isEqualTo("after the flood");
        assertThat(client.state()).isEqualTo(IrcClient.State.READY);
    }

    @Test
    @DisplayName("quitAndClose sends QUIT, lands CLOSED, ends the reader task, never redials")
    void quitClosesCleanlyWithNoLeak() throws Exception {
        client = newClient("nmox-user");
        client.connect();
        server.completeRegistration("nmox-user", T);
        client.awaitState(IrcClient.State.READY, T);
        int acceptsBefore = server.acceptCount();

        client.quitAndClose("bye now");
        assertThat(server.awaitLine("QUIT ", T)).isEqualTo("QUIT :bye now");
        assertThat(client.awaitState(IrcClient.State.CLOSED, T)).isTrue();
        assertThat(client.awaitReaderFinished(T))
                .as("the reader RP task must end — no thread leak")
                .isTrue();

        Thread.sleep(300); // several backoff periods at baseBackoffMs=50
        assertThat(server.acceptCount())
                .as("a closed client must never reconnect")
                .isEqualTo(acceptsBefore);
    }

    @Test
    @DisplayName("A dropped connection reconnects with backoff and rejoins its channels")
    void reconnectRejoinsChannels() throws Exception {
        client = newClient("nmox-user");
        client.connect();
        server.completeRegistration("nmox-user", T);
        client.awaitState(IrcClient.State.READY, T);
        client.join("#persist");
        server.awaitLine("JOIN ", T);
        server.send(":nmox-user!u@h JOIN #persist");

        server.dropClient();
        // the engine announces the drop, then redials on its own
        server.completeRegistration("nmox-user", T);
        assertThat(server.awaitLine("JOIN ", T))
                .as("auto-rejoin after re-registration")
                .isEqualTo("JOIN #persist");
        assertThat(client.awaitState(IrcClient.State.READY, T)).isTrue();
        assertThat(listener.events).contains("disconnected");
        assertThat(server.acceptCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("With a stored NickServ password, IDENTIFY follows 001 — and only then")
    void nickServIdentifyAfterWelcome() throws Exception {
        IrcSecrets.save("testnet", "hunter2"); // in-memory fallback mode
        client = newClient("nmox-user");
        client.connect();
        server.completeRegistration("nmox-user", T);
        assertThat(server.awaitLine("PRIVMSG NickServ :", T))
                .isEqualTo("PRIVMSG NickServ :IDENTIFY hunter2");
        // the identify line is outgoing: listeners never see an echo of it
        assertThat(listener.lines.stream()
                .anyMatch(m -> m.render().contains("hunter2"))).isFalse();
    }

    @Test
    @DisplayName("readLineBounded keeps terminator parity and truncates past the cap")
    void readLineBoundedUnit() throws Exception {
        BufferedReader r = new BufferedReader(new StringReader("one\r\ntwo\nthree\rfour"));
        assertThat(IrcClient.readLineBounded(r, 100)).isEqualTo("one");
        assertThat(IrcClient.readLineBounded(r, 100)).isEqualTo("two");
        assertThat(IrcClient.readLineBounded(r, 100)).isEqualTo("three");
        assertThat(IrcClient.readLineBounded(r, 100)).isEqualTo("four");
        assertThat(IrcClient.readLineBounded(r, 100)).isNull();

        BufferedReader big = new BufferedReader(new StringReader("x".repeat(500) + "\nnext"));
        String capped = IrcClient.readLineBounded(big, 100);
        assertThat(capped).startsWith("x".repeat(100)).endsWith(IrcClient.TRUNCATION_MARKER);
        assertThat(IrcClient.readLineBounded(big, 100))
                .as("the flooded line's tail is drained; the next line is intact")
                .isEqualTo("next");
    }

    @Test
    @DisplayName("Adding the same listener twice delivers once (equality-guarded add)")
    void listenerAddIsEqualityGuarded() throws Exception {
        client = newClient("nmox-user");
        client.addListener(listener); // second add of the same instance
        client.connect();
        server.completeRegistration("nmox-user", T);
        client.awaitState(IrcClient.State.READY, T);
        server.send(":x!y@z PRIVMSG nmox-user :once please");
        listener.awaitCommand("PRIVMSG", T);
        // a double-registered listener would deliver the same message twice
        IrcMessage dup = listener.lines.poll(400, TimeUnit.MILLISECONDS);
        assertThat(dup).as("no duplicate delivery").isNull();
    }
}

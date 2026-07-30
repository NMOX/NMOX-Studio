package org.nmox.studio.ui.irc.engine;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.irc.protocol.IrcMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IRCv3 against the fake server: the CAP LS→REQ→ACK→END dance
 * (intersection only, multiline LS, NAK fallback, pre-CAP servers),
 * SASL PLAIN before CAP END (happy 903, failing 904 with NO password
 * retry), sasl-only-when-usable, and the engine-side ignore filter.
 * The SASL password rides {@link IrcSecrets}' in-memory fallback —
 * tests never touch the OS keychain.
 */
class IrcCapSaslTest {

    private static final long T = 8_000;

    private FakeIrcServer server;
    private IrcClient client;
    private Recorder listener;
    private boolean originalKeyring;

    /** Minimal recording listener; asserts ride the line queue. */
    static final class Recorder implements IrcClient.Listener {

        final BlockingQueue<IrcMessage> lines = new LinkedBlockingQueue<>();

        @Override
        public void connected() {
        }

        @Override
        public void registered(String nick) {
        }

        @Override
        public void lineReceived(IrcMessage message) {
            lines.add(message);
        }

        @Override
        public void disconnected(String reason) {
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
        listener = new Recorder();
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

    private IrcClient newClient(String nick, String saslAccount) {
        IrcClient c = new IrcClient(new IrcClient.Profile(
                "testnet", "127.0.0.1", server.port(), false, nick, null, null, saslAccount));
        c.baseBackoffMs = 50;
        c.addListener(listener);
        return c;
    }

    @Test
    @DisplayName("Happy path: LS→REQ (supported∩offered)→ACK→SASL PLAIN→903→END→001")
    void fullCapSaslHappyPath() throws Exception {
        IrcSecrets.save("testnet", "sekret"); // in-memory fallback
        client = newClient("nmox-user", "acct");
        client.connect();

        server.awaitLine("CAP LS 302", T);
        server.awaitLine("NICK ", T);
        server.awaitLine("USER ", T);
        server.send(":fake.server CAP * LS :sasl=PLAIN server-time echo-message"
                + " multi-prefix unknown-cap another-unknown");
        String req = server.awaitLine("CAP REQ :", T);
        assertThat(req)
                .as("exactly supported∩offered, sorted — never an unknown cap")
                .isEqualTo("CAP REQ :echo-message multi-prefix sasl server-time");
        server.send(":fake.server CAP nmox-user ACK :echo-message multi-prefix sasl server-time");

        String payload = server.completeSaslPlain("nmox-user", T);
        assertThat(Base64.getDecoder().decode(payload))
                .as("authzid NUL authcid NUL password")
                .isEqualTo("\0acct\0sekret".getBytes(StandardCharsets.UTF_8));

        server.finishCapRegistration("nmox-user", T);
        assertThat(client.awaitState(IrcClient.State.READY, T)).isTrue();
        assertThat(client.capEnabled("server-time")).isTrue();
        assertThat(client.capEnabled("echo-message")).isTrue();
        assertThat(client.capEnabled("away-notify")).as("never offered").isFalse();
        assertThat(server.sawLine("PRIVMSG NickServ", 400))
                .as("SASL replaces the NickServ fallback — no second identify")
                .isFalse();
    }

    @Test
    @DisplayName("A multiline CAP LS (the * continuation) still REQs the union")
    void multilineCapLs() throws Exception {
        client = newClient("nmox-user", null);
        client.connect();
        server.awaitLine("CAP LS 302", T);
        server.awaitLine("NICK ", T);
        server.awaitLine("USER ", T);
        server.send(":fake.server CAP * LS * :server-time");
        server.send(":fake.server CAP * LS :multi-prefix away-notify");
        assertThat(server.awaitLine("CAP REQ :", T))
                .isEqualTo("CAP REQ :away-notify multi-prefix server-time");
        server.send(":fake.server CAP nmox-user ACK :away-notify multi-prefix server-time");
        server.finishCapRegistration("nmox-user", T);
        assertThat(client.awaitState(IrcClient.State.READY, T)).isTrue();
        assertThat(client.capEnabled("away-notify")).isTrue();
    }

    @Test
    @DisplayName("NAK ends negotiation and registration proceeds bare")
    void nakFallsBackBare() throws Exception {
        client = newClient("nmox-user", null);
        client.connect();
        server.awaitLine("CAP LS 302", T);
        server.awaitLine("NICK ", T);
        server.awaitLine("USER ", T);
        server.send(":fake.server CAP * LS :server-time multi-prefix");
        server.awaitLine("CAP REQ :", T);
        server.send(":fake.server CAP nmox-user NAK :server-time multi-prefix");
        server.finishCapRegistration("nmox-user", T);
        assertThat(client.awaitState(IrcClient.State.READY, T)).isTrue();
        assertThat(client.capEnabled("server-time")).isFalse();
    }

    @Test
    @DisplayName("SASL 904 failure: honest line, CAP END, registration continues, NO retry")
    void saslFailureNeverRetries() throws Exception {
        IrcSecrets.save("testnet", "wrongpass");
        client = newClient("nmox-user", "acct");
        client.connect();
        server.awaitLine("CAP LS 302", T);
        server.awaitLine("NICK ", T);
        server.awaitLine("USER ", T);
        server.send(":fake.server CAP * LS :sasl");
        server.awaitLine("CAP REQ :", T);
        server.send(":fake.server CAP nmox-user ACK :sasl");
        server.awaitLine("AUTHENTICATE PLAIN", T);
        server.send(":fake.server 904 nmox-user :SASL authentication failed");
        server.finishCapRegistration("nmox-user", T);
        assertThat(client.awaitState(IrcClient.State.READY, T)).isTrue();
        // the 904 reached listeners so the UI can render an honest line
        listener.awaitCommand("904", T);
        assertThat(server.sawLine("AUTHENTICATE", 400))
                .as("a failed password is NEVER retried (lockout machine otherwise)")
                .isFalse();
    }

    @Test
    @DisplayName("sasl is REQ'd only when usable: account without password skips it")
    void saslSkippedWithoutPassword() throws Exception {
        // no IrcSecrets.save — the account alone is not enough
        client = newClient("nmox-user", "acct");
        client.connect();
        server.awaitLine("CAP LS 302", T);
        server.awaitLine("NICK ", T);
        server.awaitLine("USER ", T);
        server.send(":fake.server CAP * LS :sasl server-time");
        assertThat(server.awaitLine("CAP REQ :", T))
                .isEqualTo("CAP REQ :server-time");
        server.send(":fake.server CAP nmox-user ACK :server-time");
        server.finishCapRegistration("nmox-user", T);
        assertThat(client.awaitState(IrcClient.State.READY, T)).isTrue();
    }

    @Test
    @DisplayName("No caps in common: CAP END goes out with no REQ at all")
    void noCommonCapsEndsImmediately() throws Exception {
        client = newClient("nmox-user", null);
        client.connect();
        server.awaitLine("CAP LS 302", T);
        server.awaitLine("NICK ", T);
        server.awaitLine("USER ", T);
        server.send(":fake.server CAP * LS :their-custom-thing");
        // the next CAP line the client sends must be END, not REQ
        assertThat(server.awaitLine("CAP ", T)).isEqualTo("CAP END");
        server.send(":fake.server 001 nmox-user :Welcome");
        assertThat(client.awaitState(IrcClient.State.READY, T)).isTrue();
    }

    @Test
    @DisplayName("A pre-CAP server that ignores CAP LS still registers (001 clears the dance)")
    void preCapServerStillRegisters() throws Exception {
        IrcSecrets.save("testnet", "hunter2");
        client = newClient("nmox-user", null);
        client.connect();
        server.completeRegistration("nmox-user", T); // never answers CAP
        assertThat(client.awaitState(IrcClient.State.READY, T)).isTrue();
        // and the NickServ fallback still runs for no-SASL profiles
        assertThat(server.awaitLine("PRIVMSG NickServ :", T))
                .isEqualTo("PRIVMSG NickServ :IDENTIFY hunter2");
    }

    @Test
    @DisplayName("Ignored nicks' PRIVMSG/NOTICE vanish before listeners; JOIN still flows")
    void ignoredNickIsDropped() throws Exception {
        client = newClient("nmox-user", null);
        client.connect();
        server.completeRegistration("nmox-user", T);
        client.awaitState(IrcClient.State.READY, T);
        client.setIgnoredNicks(List.of("Troll"));

        server.send(":troll!u@h PRIVMSG #chan :you never see this");
        server.send(":troll!u@h NOTICE nmox-user :nor this");
        server.send(":troll!u@h JOIN #chan");
        server.send(":friend!u@h PRIVMSG #chan :but this arrives");

        IrcMessage join = listener.awaitCommand("JOIN", T);
        assertThat(join.nick()).as("presence is not speech — JOIN flows").isEqualTo("troll");
        IrcMessage msg = listener.awaitCommand("PRIVMSG", T);
        assertThat(msg.nick()).isEqualTo("friend");
        assertThat(listener.lines.stream()
                .noneMatch(m -> "troll".equalsIgnoreCase(String.valueOf(m.nick()))
                && ("PRIVMSG".equals(m.command()) || "NOTICE".equals(m.command()))))
                .isTrue();
    }

    @Test
    @DisplayName("An @time tag survives parsing end to end (server-time's raw material)")
    void serverTimeTagDelivered() throws Exception {
        client = newClient("nmox-user", null);
        client.connect();
        server.completeRegistration("nmox-user", T);
        client.awaitState(IrcClient.State.READY, T);
        server.send("@time=2026-07-30T06:07:08.000Z :a!u@h PRIVMSG #c :tagged");
        IrcMessage m = listener.awaitCommand("PRIVMSG", T);
        assertThat(m.tags()).containsEntry("time", "2026-07-30T06:07:08.000Z");
    }
}

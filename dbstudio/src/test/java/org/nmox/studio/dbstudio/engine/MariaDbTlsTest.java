package org.nmox.studio.dbstudio.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.core.util.Threads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB Studio speaks TLS to MySQL and MariaDB (3.9.0), measured against the
 * driver this build ships rather than read from its release notes.
 *
 * <p>MariaDB Connector/J 4.0 changed its default {@code sslMode} from
 * {@code disable} to {@code verify-full}. Every {@code .nmoxdb.json}
 * written before 3.9.0 carries {@code secure=false}, so a URL that left
 * the mode to the driver would have turned every plain local connection
 * into a refusal on the day of the bump. The URL therefore always says
 * its mode: {@code secure} is verify-full, off is the cleartext those
 * specs always had.
 *
 * <p>The server here is a fake that speaks exactly one packet each way:
 * the protocol-10 greeting with the SSL capability cleared (the shape a
 * server without TLS announces), and an ERR packet for whatever the
 * client sends next. That is enough to see which branch the driver takes
 * — a TLS-mode connect refuses before it sends a byte, a plain connect
 * sends its handshake and receives the server's error — and to pin the
 * driver's exact words for {@link JdbcCore#humanize}.
 */
class MariaDbTlsTest {

    private static ConnectionSpec spec(DbEngine engine, int port, boolean secure) {
        return new ConnectionSpec("id", "t", engine, "127.0.0.1", port, "db", "u", null, secure);
    }

    @Test
    @DisplayName("the driver this build ships dials TLS unless told otherwise, so every MySQL/MariaDB URL says its mode")
    void driverDefaultIsTlsAndTheUrlNeverLeavesItToTheDriver() throws Exception {
        // the fact that makes this release a decision rather than a bump
        org.mariadb.jdbc.Configuration bare = org.mariadb.jdbc.Configuration.parse("jdbc:mariadb://h:3306/d");
        assertThat(bare.sslMode()).as("Connector/J 4.0's own default")
                .isEqualTo(org.mariadb.jdbc.export.SslMode.VERIFY_FULL);

        for (DbEngine e : new DbEngine[]{DbEngine.MYSQL, DbEngine.MARIADB}) {
            String plain = DbClient.connectionUrl(spec(e, 3306, false));
            String tls = DbClient.connectionUrl(spec(e, 3306, true));
            assertThat(org.mariadb.jdbc.Configuration.parse(plain).sslMode())
                    .as("%s, Use TLS off: the connection a pre-3.9.0 spec always had", e)
                    .isEqualTo(org.mariadb.jdbc.export.SslMode.DISABLE);
            assertThat(org.mariadb.jdbc.Configuration.parse(tls).sslMode())
                    .as("%s, Use TLS on: the certificate is verified, never merely trusted", e)
                    .isEqualTo(org.mariadb.jdbc.export.SslMode.VERIFY_FULL);
            // the connect timeout the URL always carried is still there
            assertThat(org.mariadb.jdbc.Configuration.parse(plain).connectTimeout()).isEqualTo(5000);
        }
        // the other JDBC engines are untouched by a MariaDB-driver option
        assertThat(DbClient.connectionUrl(spec(DbEngine.POSTGRES, 5432, true))).doesNotContain("sslMode");
    }

    @Test
    @DisplayName("Use TLS against a server that offers none refuses before a byte of credentials leaves, and the refusal reads human")
    void tlsAgainstAPlainServerRefusesAndSpeaks() throws Exception {
        try (FakeServer server = FakeServer.start()) {
            SQLException e = connect(spec(DbEngine.MARIADB, server.port(), true));
            assertThat(e.getMessage().toLowerCase(java.util.Locale.ROOT))
                    .as("the driver's own words name TLS").contains("ssl");
            assertThat(server.received()).as("nothing after the greeting: the client never sent its handshake")
                    .isZero();
            String human = JdbcCore.humanize(e);
            assertThat(human).as("the refusal says what to do").contains("Use TLS").contains(e.getMessage());
        }
    }

    @Test
    @DisplayName("Use TLS off reaches the server in the clear, exactly as before the driver bump")
    void plainConnectReachesTheServer() throws Exception {
        try (FakeServer server = FakeServer.start()) {
            SQLException e = connect(spec(DbEngine.MARIADB, server.port(), false));
            assertThat(server.received()).as("the client sent its handshake response").isPositive();
            assertThat(e.getMessage()).as("the fake server's own ERR packet came back through the driver")
                    .contains("fake server saw a plain handshake");
            assertThat(JdbcCore.humanize(e)).doesNotContain("Use TLS");
        }
    }

    private static SQLException connect(ConnectionSpec spec) throws Exception {
        Class.forName(spec.engine().driverClass());
        Properties p = new Properties();
        p.setProperty("user", "u");
        p.setProperty("password", "x");
        AtomicReference<SQLException> got = new AtomicReference<>();
        assertThatThrownBy(() -> {
            try {
                DriverManager.getConnection(DbClient.connectionUrl(spec), p).close();
            } catch (SQLException e) {
                got.set(e);
                throw e;
            }
        }).isInstanceOf(SQLException.class);
        return got.get();
    }

    /** One greeting without CLIENT_SSL, then an ERR for whatever arrives. */
    static final class FakeServer implements AutoCloseable {
        private final ServerSocket socket;
        private final Thread thread;
        private volatile int received;

        private FakeServer(ServerSocket socket) {
            this.socket = socket;
            this.thread = Threads.daemon(this::serve, "fake-mariadb");
        }

        static FakeServer start() throws IOException {
            ServerSocket ss = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
            FakeServer s = new FakeServer(ss);
            s.thread.start();
            return s;
        }

        int port() {
            return socket.getLocalPort();
        }

        int received() {
            return received;
        }

        private void serve() {
            try (Socket c = socket.accept()) {
                c.setSoTimeout(3000);
                OutputStream out = c.getOutputStream();
                out.write(packet(0, greetingWithoutSsl()));
                out.flush();
                InputStream in = c.getInputStream();
                byte[] buf = new byte[4096];
                int n;
                try {
                    n = in.read(buf);
                } catch (IOException timeoutOrReset) {
                    n = -1;
                }
                if (n > 0) {
                    received = n;
                    out.write(packet(2, err("fake server saw a plain handshake")));
                    out.flush();
                }
            } catch (IOException closed) {
                // the test closes the socket; nothing to report
            }
        }

        private static byte[] packet(int seq, byte[] payload) {
            byte[] p = new byte[payload.length + 4];
            p[0] = (byte) payload.length;
            p[1] = (byte) (payload.length >> 8);
            p[2] = (byte) (payload.length >> 16);
            p[3] = (byte) seq;
            System.arraycopy(payload, 0, p, 4, payload.length);
            return p;
        }

        /** Protocol 10 handshake, capabilities WITHOUT CLIENT_SSL (0x800). */
        private static byte[] greetingWithoutSsl() throws IOException {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            b.write(0x0a);
            b.write("11.4.0-MariaDB".getBytes(StandardCharsets.US_ASCII));
            b.write(0);
            b.write(new byte[]{1, 0, 0, 0}); // connection id
            b.write("12345678".getBytes(StandardCharsets.US_ASCII)); // scramble part 1
            b.write(0); // filler
            // lower capabilities: LONG_PASSWORD 1 | LONG_FLAG 4 | PROTOCOL_41 0x200 | SECURE_CONNECTION 0x8000
            int lower = 0x1 | 0x4 | 0x200 | 0x8000;
            b.write(lower & 0xff);
            b.write((lower >> 8) & 0xff);
            b.write(0x2d); // utf8mb4
            b.write(new byte[]{2, 0}); // status: autocommit
            // upper capabilities: PLUGIN_AUTH 0x80000 -> 0x0008 in the upper half
            b.write(0x08);
            b.write(0x00);
            b.write(21); // auth plugin data length
            b.write(new byte[10]); // reserved
            b.write("123456789012".getBytes(StandardCharsets.US_ASCII)); // scramble part 2
            b.write(0);
            b.write("mysql_native_password".getBytes(StandardCharsets.US_ASCII));
            b.write(0);
            return b.toByteArray();
        }

        private static byte[] err(String message) throws IOException {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            b.write(0xff);
            b.write(1045 & 0xff);
            b.write(1045 >> 8);
            b.write('#');
            b.write("28000".getBytes(StandardCharsets.US_ASCII));
            b.write(message.getBytes(StandardCharsets.UTF_8));
            return b.toByteArray();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}

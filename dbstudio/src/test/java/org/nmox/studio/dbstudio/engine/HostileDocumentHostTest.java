package org.nmox.studio.dbstudio.engine;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mongodb.ConnectionString;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link HostileSpecUrlTest}'s question, asked of the two document
 * backends (ledger 142 left it as a hypothesis). Their addresses are built
 * by the same concatenation from the same stranger's text: a
 * {@code git clone} brings the host in {@code .nmoxdb.json}.
 *
 * <p>Measured first, against the MongoDB driver this build ships and
 * {@code java.net.URI}; then the product is held to refusing in words
 * before anything is dialed. The one socket here is a loopback server
 * standing in for "the other host", and the test passes when it hears
 * nothing.
 */
class HostileDocumentHostTest {

    private static final String TAIL = ":27017/?connectTimeoutMS=5000&serverSelectionTimeoutMS=5000";

    private static ConnectionSpec spec(DbEngine engine, String host, int port) {
        return new ConnectionSpec("id", "t", engine, host, port, "shop", "u", "", false);
    }

    @Test
    @DisplayName("measured: the MongoDB driver starts its options at the first /? in the host, and the appended timeouts are lost")
    void whatTheShippedMongoDriverDoesWithAHostThatHoldsASlash() {
        // the strings the product built for these hosts before the rule, written out by hand
        ConnectionString proxied = new ConnectionString(
                "mongodb://u:pw@db.internal.invalid/?proxyHost=elsewhere.invalid&x=" + TAIL);
        assertThat(proxied.getProxyHost())
                .as("the session, and the login, through a SOCKS proxy the dialog never showed")
                .isEqualTo("elsewhere.invalid");
        assertThat(proxied.getConnectTimeout())
                .as("and the five-second bound appended after the host is no longer an option at all")
                .isNull();
        ConnectionString plain = new ConnectionString(
                "mongodb://u:pw@db.internal.invalid/admin?authMechanism=PLAIN&x=" + TAIL);
        assertThat(plain.getCredential().getAuthenticationMechanism())
                .as("PLAIN sends the keychain password itself, where the default sends a proof of it")
                .isEqualTo(com.mongodb.AuthenticationMechanism.PLAIN);
        assertThat(new ConnectionString("mongodb://u:pw@db.internal.invalid/?tlsInsecure=true&x=" + TAIL)
                .getSslInvalidHostnameAllowed()).isTrue();
        // and what a well-formed one looks like, for contrast
        ConnectionString ordinary = new ConnectionString("mongodb://u:pw@db.internal.invalid" + TAIL);
        assertThat(ordinary.getProxyHost()).isNull();
        assertThat(ordinary.getConnectTimeout()).isEqualTo(5000);
    }

    @Test
    @DisplayName("measured: java.net.URI reads name@other as user-info and a host, so the request goes to the other")
    void whatUriDoesWithAHostThatHoldsAnAtSign() {
        URI u = URI.create("http://db.internal.invalid@elsewhere.invalid:5984/_all_dbs");
        assertThat(u.getHost()).isEqualTo("elsewhere.invalid");
        assertThat(u.getUserInfo()).isEqualTo("db.internal.invalid");
        assertThat(URI.create("http://elsewhere.invalid/:5984/_all_dbs").getPort())
                .as("a slash ends the authority: the port the dialog shows is path").isEqualTo(-1);
    }

    @Test
    @DisplayName("MongoDB: a host holding URL syntax is refused in words by Test and Connect, and no connection string is built")
    void mongoRefusesBeforeAnythingIsBuilt() {
        for (String host : new String[]{
            "db.internal.invalid/?proxyHost=elsewhere.invalid&x=", "db.internal.invalid/admin?authMechanism=PLAIN&x=",
            "db.internal.invalid?x=", "db.internal.invalid@elsewhere.invalid", "h#x", "h x", "h\nx", "h\\x"}) {
            MongoBackend backend = new MongoBackend(spec(DbEngine.MONGODB, host, 27017), "pw".toCharArray());
            try {
                assertThat(backend.test()).as("Test, %s", host).isEqualTo(Bundle.DbClient_carriesOptions());
                assertThat(backend.open()).as("Connect, %s", host).isEqualTo(Bundle.DbClient_carriesOptions());
                assertThat(backend.isOpen()).isFalse();
            } finally {
                backend.close();
            }
            assertThatThrownBy(() -> MongoBackend.connectionString("u", "pw".toCharArray(), host, 27017))
                    .as("the backstop, %s", host).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("CouchDB: name@other dials nothing; the other host hears no request and no credentials")
    void couchSendsNothingToTheOtherHost() throws Exception {
        List<String> heard = new CopyOnWriteArrayList<>();
        HttpServer other = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        other.createContext("/", exchange -> {
            heard.add(exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"couchdb\":\"Welcome\",\"version\":\"3.5.0\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        other.start();
        try {
            int port = other.getAddress().getPort();
            // the control: the stand-in is reachable and answers like CouchDB, so silence below is a refusal
            CouchBackend control = new CouchBackend(spec(DbEngine.COUCHDB, "127.0.0.1", port), "pw".toCharArray());
            assertThat(control.test()).as("the control connects").isNull();
            assertThat(heard).as("and its credentials arrive").hasSize(1).first().asString().contains("Basic ");
            heard.clear();

            for (String host : new String[]{"db.internal.invalid@127.0.0.1", "127.0.0.1/x?", "127.0.0.1#"}) {
                CouchBackend backend = new CouchBackend(spec(DbEngine.COUCHDB, host, port), "pw".toCharArray());
                assertThat(backend.test()).as("Test, %s", host).isEqualTo(Bundle.DbClient_carriesOptions());
                assertThat(backend.open()).as("Connect, %s", host).isEqualTo(Bundle.DbClient_carriesOptions());
                assertThat(backend.isOpen()).isFalse();
                // the calls that do not pass through test(): the tree and the console
                assertThat(backend.listContainers()).as("the tree, %s", host).isEmpty();
                List<QueryResult> run = backend.runConsole("{\"selector\":{}}", 10);
                assertThat(run).as("the console, %s", host).allMatch(QueryResult::isError);
            }
            // with no password stored the refusal is still this sentence alone: Test answers
            // before the request is attempted, so no "enter a password" advice is added to it
            CouchBackend noPassword = new CouchBackend(spec(DbEngine.COUCHDB, "db.internal.invalid@127.0.0.1", port), null);
            assertThat(noPassword.test()).isEqualTo(Bundle.DbClient_carriesOptions());
            assertThat(heard).as("the other host heard nothing").isEmpty();
        } finally {
            other.stop(0);
        }
    }

    @Test
    @DisplayName("ordinary hosts are left alone, and a bare IPv6 address gets the brackets a URL needs")
    void ordinaryHostsAreLeftAlone() {
        for (String host : new String[]{"localhost", "db.example.com", "my_host", "db-1.internal", "10.0.0.5",
            "[::1]", "h1,h2", "bücher.example", "fe80::1%en0", ""}) {
            assertThat(DbClient.hostCarriesUrlSyntax(host)).as(host).isFalse();
        }
        assertThat(DbClient.urlHost("::1")).isEqualTo("[::1]");
        assertThat(DbClient.urlHost("fe80::1")).isEqualTo("[fe80::1]");
        assertThat(DbClient.urlHost("[::1]")).isEqualTo("[::1]");
        assertThat(DbClient.urlHost("db.example.com")).isEqualTo("db.example.com");
        assertThat(DbClient.urlHost("h1:27018,h2")).as("a host list keeps its own ports").isEqualTo("h1:27018,h2");
        assertThat(DbClient.urlHost(null)).isEmpty();
        // measured before the rule: the driver threw on ::1 and URI answered a null host
        assertThat(new ConnectionString(MongoBackend.connectionString(null, null, "::1", 27017)).getHosts())
                .containsExactly("[::1]:27017");
        assertThat(URI.create(new CouchBackend(spec(DbEngine.COUCHDB, "::1", 5984), null).baseUrl()).getHost())
                .isEqualTo("[::1]");
    }
}

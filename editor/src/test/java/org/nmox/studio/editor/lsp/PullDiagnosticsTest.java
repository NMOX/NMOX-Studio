package org.nmox.studio.editor.lsp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A server that reports problems only when asked is asked, and the client
 * sees what it has always seen (3.10.0).
 *
 * <p>Measured before the class was written, on TypeScript 7.0.2's
 * {@code tsc --lsp --stdio}: a file with two type errors was opened by a
 * client that declares what the platform's declares, and no
 * {@code publishDiagnostics} arrived for it; {@code textDocument/diagnostic}
 * for the same file returned both. The answer in {@link #REAL_ANSWER} is that
 * server's, as it wrote it.
 *
 * <p>Nothing here sleeps: the timer is stepped by hand and the server is a
 * queue of bytes.
 */
class PullDiagnosticsTest {

    /** What TypeScript 7.0.2 answered for {@code const n: number = "x";}, verbatim. */
    private static final String REAL_ANSWER = "{\"kind\":\"full\",\"items\":[{\"range\":{\"start\":{\"line\":0,\"character\":6},"
            + "\"end\":{\"line\":0,\"character\":7}},\"severity\":1,\"code\":2322,\"source\":\"ts\","
            + "\"message\":\"Type 'string' is not assignable to type 'number'.\"},{\"range\":{\"start\":{\"line\":0,"
            + "\"character\":6},\"end\":{\"line\":0,\"character\":7}},\"severity\":4,\"code\":6133,\"source\":\"ts\","
            + "\"message\":\"'n' is declared but its value is never read.\"}]}";

    private static final String A = "file:///Users/ada/shop/src/main.ts";
    private static final String B = "file:///Users/ada/shop/src/util.ts";

    /** The server's output: what the test has "written" so far. Reading past it ends the stream. */
    private static final class Server extends InputStream {
        private final ByteArrayOutputStream said = new ByteArrayOutputStream();
        private int at;

        void says(String body) {
            says(PullDiagnostics.frame(body.getBytes(StandardCharsets.UTF_8)));
        }

        void says(byte[] raw) {
            said.write(raw, 0, raw.length);
        }

        @Override
        public int read() {
            byte[] all = said.toByteArray();
            return at < all.length ? all[at++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            int n = Math.min(len, said.size() - at);
            if (n <= 0) {
                return len == 0 ? 0 : -1;
            }
            System.arraycopy(said.toByteArray(), at, b, off, n);
            at += n;
            return n;
        }
    }

    private ByteArrayOutputStream toServer;
    private Server server;
    private final List<Integer> armed = new ArrayList<>();
    private final AtomicLong now = new AtomicLong(1_000);
    private PullDiagnostics bridge;
    private OutputStream client;

    @BeforeEach
    void connect() {
        toServer = new ByteArrayOutputStream();
        server = new Server();
        armed.clear();
        bridge = new PullDiagnostics(toServer, server, "tsc", armed::add, now::get);
        client = bridge.toServer();
    }

    private void clientWrites(String body) throws IOException {
        client.write(PullDiagnostics.frame(body.getBytes(StandardCharsets.UTF_8)));
    }

    private void opens(String uri) throws IOException {
        clientWrites(note("textDocument/didOpen", new JSONObject().put("textDocument",
                new JSONObject().put("uri", uri).put("languageId", "typescript").put("version", 1).put("text", "x"))));
    }

    private void changes(String uri) throws IOException {
        clientWrites(note("textDocument/didChange", new JSONObject().put("textDocument",
                new JSONObject().put("uri", uri).put("version", 2)).put("contentChanges", new JSONArray())));
    }

    private void closes(String uri) throws IOException {
        clientWrites(note("textDocument/didClose", new JSONObject().put("textDocument", new JSONObject().put("uri", uri))));
    }

    private static String note(String method, JSONObject params) {
        return new JSONObject().put("jsonrpc", "2.0").put("method", method).put("params", params).toString();
    }

    /** Every frame in a run of bytes, as its body. */
    private static List<String> frames(byte[] raw) {
        List<String> bodies = new ArrayList<>();
        int at = 0;
        while (at < raw.length) {
            int end = at;
            while (!(raw[end] == '\r' && raw[end + 1] == '\n' && raw[end + 2] == '\r' && raw[end + 3] == '\n')) {
                end++;
            }
            int length = DiagnosticsTap.contentLength(new String(raw, at, end - at, StandardCharsets.US_ASCII));
            bodies.add(new String(raw, end + 4, length, StandardCharsets.UTF_8));
            at = end + 4 + length;
        }
        return bodies;
    }

    /** What reached the server since the last call. */
    private List<JSONObject> serverGot() {
        List<JSONObject> got = new ArrayList<>();
        for (String body : frames(toServer.toByteArray())) {
            got.add(new JSONObject(body));
        }
        toServer.reset();
        return got;
    }

    /** The questions among them: the documents asked about, in order, and their ids. */
    private static List<String[]> questions(List<JSONObject> got) {
        List<String[]> asked = new ArrayList<>();
        for (JSONObject m : got) {
            if ("textDocument/diagnostic".equals(m.optString("method"))) {
                assertThat(m.getString("id")).as("a question carries this class's own id").startsWith(PullDiagnostics.ID_PREFIX);
                asked.add(new String[] {m.getJSONObject("params").getJSONObject("textDocument").getString("uri"), m.getString("id")});
            }
        }
        return asked;
    }

    /** Everything the client can read: the server has said all it will. */
    private List<JSONObject> clientSees() throws IOException {
        List<JSONObject> seen = new ArrayList<>();
        for (String body : frames(bridge.fromServer().readAllBytes())) {
            seen.add(new JSONObject(body));
        }
        return seen;
    }

    private static String answer(String id, String result) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + id + "\",\"result\":" + result + "}";
    }

    @Test
    @DisplayName("an opened document is asked about, and the answer reaches the client as the problems of that document")
    void anOpenedDocumentIsAskedAbout() throws Exception {
        opens(A);
        assertThat(questions(serverGot())).as("nothing is asked on the client's thread").isEmpty();
        assertThat(armed).as("the question is a moment away").containsExactly(PullDiagnostics.OPEN_MILLIS);

        bridge.work();
        List<String[]> asked = questions(serverGot());
        assertThat(asked).hasSize(1);
        assertThat(asked.get(0)[0]).isEqualTo(A);

        server.says(answer(asked.get(0)[1], REAL_ANSWER));
        List<JSONObject> seen = clientSees();
        assertThat(seen).as("one message, and it is not the answer").hasSize(1);
        JSONObject published = seen.get(0);
        assertThat(published.getString("method")).isEqualTo("textDocument/publishDiagnostics");
        assertThat(published.has("id")).isFalse();
        JSONObject params = published.getJSONObject("params");
        assertThat(params.getString("uri")).isEqualTo(A);
        assertThat(params.getJSONArray("diagnostics").similar(new JSONObject(REAL_ANSWER).getJSONArray("items")))
                .as("the server's own items, unchanged").isTrue();
    }

    @Test
    @DisplayName("every byte that is not this class's business passes unchanged, however it is cut up")
    void everythingElsePassesUnchanged() throws Exception {
        byte[] hover = PullDiagnostics.frame("{\"jsonrpc\":\"2.0\",\"id\":\"7\",\"method\":\"textDocument/hover\",\"params\":{}}"
                .getBytes(StandardCharsets.UTF_8));
        byte[] resolve = PullDiagnostics.frame("{\"jsonrpc\":\"2.0\",\"id\":\"8\",\"method\":\"completionItem/resolve\",\"params\":{\"é\":1}}"
                .getBytes(StandardCharsets.UTF_8));
        for (byte b : hover) {
            client.write(b); // a byte at a time
        }
        byte[] two = new byte[resolve.length * 2];
        System.arraycopy(resolve, 0, two, 0, resolve.length);
        System.arraycopy(resolve, 0, two, resolve.length, resolve.length);
        client.write(two); // two frames in one write
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(hover);
        expected.write(two);
        assertThat(toServer.toByteArray()).as("the client's requests, as it wrote them").isEqualTo(expected.toByteArray());

        byte[] reply = PullDiagnostics.frame("{\"jsonrpc\":\"2.0\",\"id\":\"7\",\"result\":{\"contents\":\"x\"}}".getBytes(StandardCharsets.UTF_8));
        byte[] own = PullDiagnostics.frame(("{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/publishDiagnostics\",\"params\":"
                + "{\"uri\":\"file:///users/ada/shop/tsconfig.json\",\"diagnostics\":[]}}").getBytes(StandardCharsets.UTF_8));
        // a server may end its header lines with a bare line feed, and a message may quote this class's id
        String bareBody = "{\"jsonrpc\":\"2.0\",\"method\":\"window/logMessage\",\"params\":\"p:1\"}";
        byte[] bare = ("Content-Length: " + bareBody.length() + "\n\n" + bareBody).getBytes(StandardCharsets.UTF_8);
        byte[] quoting = PullDiagnostics.frame(("{\"jsonrpc\":\"2.0\",\"method\":\"window/logMessage\",\"params\":"
                + "{\"type\":2,\"message\":\"handled nmox-pull:4, registerCapability and workspace/diagnostic/refresh\"}}")
                .getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream said = new ByteArrayOutputStream();
        for (byte[] part : new byte[][] {reply, own, bare, quoting}) {
            said.write(part);
        }
        server.says(said.toByteArray());
        ByteArrayOutputStream read = new ByteArrayOutputStream();
        InputStream in = bridge.fromServer();
        for (int c = in.read(); c >= 0; c = in.read()) {
            read.write(c); // a byte at a time
        }
        assertThat(read.toByteArray()).as("the server's messages, as it wrote them").isEqualTo(said.toByteArray());
    }

    @Test
    @DisplayName("initialize gains the two capabilities this class provides, and keeps the client's own")
    void initializeDeclaresWhatThisProvides() throws Exception {
        clientWrites("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"initialize\",\"params\":{\"processId\":41,"
                + "\"capabilities\":{\"textDocument\":{\"publishDiagnostics\":{\"relatedInformation\":true}},"
                + "\"workspace\":{\"workspaceFolders\":true}}}}");
        clientWrites("{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"initialize\",\"params\":{\"capabilities\":{}}}");
        List<JSONObject> got = serverGot();
        JSONObject capabilities = got.get(0).getJSONObject("params").getJSONObject("capabilities");
        assertThat(capabilities.getJSONObject("workspace").getJSONObject("diagnostics").getBoolean("refreshSupport")).isTrue();
        assertThat(capabilities.getJSONObject("textDocument").has("diagnostic")).isTrue();
        assertThat(capabilities.getJSONObject("workspace").getBoolean("workspaceFolders")).as("the client's own").isTrue();
        assertThat(capabilities.getJSONObject("textDocument").getJSONObject("publishDiagnostics").getBoolean("relatedInformation")).isTrue();
        assertThat(got.get(0).getJSONObject("params").getInt("processId")).isEqualTo(41);
        assertThat(got.get(1).getJSONObject("params").getJSONObject("capabilities").isEmpty())
                .as("only the first frame is the handshake").isTrue();
    }

    @Test
    @DisplayName("a change asks about every open document, the changed one first: an edit in one file breaks another")
    void aChangeAsksAboutEveryOpenDocument() throws Exception {
        opens(B);
        opens(A);
        bridge.work();
        serverGot();
        armed.clear();

        changes(A);
        assertThat(armed).containsExactly(PullDiagnostics.CHANGE_MILLIS);
        bridge.work();
        List<String[]> asked = questions(serverGot());
        assertThat(asked).extracting(q -> q[0]).containsExactly(A, B);
    }

    @Test
    @DisplayName("typing is waited out, and not for ever: a document is asked about within the longest wait")
    void typingDoesNotStarveTheQuestion() throws Exception {
        opens(A);
        bridge.work();
        armed.clear();
        changes(A);
        now.addAndGet(PullDiagnostics.MAX_WAIT_MILLIS - 100);
        changes(A);
        now.addAndGet(500);
        changes(A);
        assertThat(armed).containsExactly(PullDiagnostics.CHANGE_MILLIS, 100, 0);
        bridge.work();
        armed.clear();
        changes(A);
        assertThat(armed).as("asked, so the wait starts over").containsExactly(PullDiagnostics.CHANGE_MILLIS);
    }

    @Test
    @DisplayName("a closed document's problems are withdrawn, whatever the server still says about it")
    void aClosedDocumentIsPublishedEmpty() throws Exception {
        opens(A);
        bridge.work();
        String early = questions(serverGot()).get(0)[1];
        closes(A);
        assertThat(armed).last().isEqualTo(0);
        bridge.work();
        List<String[]> asked = questions(serverGot());
        assertThat(asked).as("asked once more, to have an answer to hang the empty list on").hasSize(1);

        // the server answers both from the file on disk, problems and all (measured)
        server.says(answer(early, REAL_ANSWER));
        server.says(answer(asked.get(0)[1], REAL_ANSWER));
        List<JSONObject> seen = clientSees();
        assertThat(seen).as("the answer for a document since closed is dropped").hasSize(1);
        assertThat(seen.get(0).getJSONObject("params").getString("uri")).isEqualTo(A);
        assertThat(seen.get(0).getJSONObject("params").getJSONArray("diagnostics").length()).isZero();
    }

    @Test
    @DisplayName("the server's request to be asked again is answered here, never shown to the client, and obeyed")
    void aRefreshIsAnsweredAndObeyed() throws Exception {
        opens(A);
        opens(B);
        bridge.work();
        serverGot();
        armed.clear();

        server.says("{\"jsonrpc\":\"2.0\",\"id\":\"ts7\",\"method\":\"workspace/diagnostic/refresh\"}");
        assertThat(clientSees()).as("a client that cannot refresh is not asked to").isEmpty();
        assertThat(armed).containsExactly(PullDiagnostics.OPEN_MILLIS);
        assertThat(serverGot()).as("the reply is not written on the reader's thread").isEmpty();
    }

    @Test
    @DisplayName("the reply to a refresh, and the questions it asks for, are written by the lane")
    void aRefreshIsObeyedOnTheLane() throws Exception {
        opens(A);
        opens(B);
        bridge.work();
        serverGot();
        server.says("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"workspace/diagnostic/refresh\"}");
        server.says("{\"jsonrpc\":\"2.0\",\"method\":\"window/logMessage\",\"params\":{\"type\":2,\"message\":\"next\"}}");
        InputStream in = bridge.fromServer();
        byte[] next = in.readNBytes(20);
        assertThat(new String(next, StandardCharsets.US_ASCII)).as("the refresh request is gone from the stream").startsWith("Content-Length: ");
        bridge.work();
        List<JSONObject> got = serverGot();
        assertThat(got.get(0).getInt("id")).as("the server's own id, as it wrote it").isEqualTo(9);
        assertThat(got.get(0).isNull("result")).isTrue();
        assertThat(got.get(0).has("result")).isTrue();
        assertThat(questions(got)).extracting(q -> q[0]).containsExactlyInAnyOrder(A, B);
    }

    @Test
    @DisplayName("the server's request to hear of configuration changes is accepted here; any other registration is the client's to refuse")
    void aConfigurationWatchIsAccepted() throws Exception {
        // TypeScript 7.0.2's own request, as it wrote it
        String asked = "{\"jsonrpc\":\"2.0\",\"id\":\"ts1\",\"method\":\"client/registerCapability\",\"params\":{\"registrations\":"
                + "[{\"id\":\"typescript-config-watch-id\",\"method\":\"workspace/didChangeConfiguration\","
                + "\"registerOptions\":{\"section\":[\"js/ts\",\"typescript\",\"javascript\",\"editor\"]}}]}}";
        String files = "{\"jsonrpc\":\"2.0\",\"id\":\"ts2\",\"method\":\"client/registerCapability\",\"params\":{\"registrations\":"
                + "[{\"id\":\"a\",\"method\":\"workspace/didChangeConfiguration\"},"
                + "{\"id\":\"b\",\"method\":\"workspace/didChangeWatchedFiles\"}]}}";
        server.says(asked);
        server.says(files);
        List<JSONObject> seen = clientSees();
        assertThat(seen).as("a promise this client would break is not made for it").hasSize(1);
        assertThat(seen.get(0).getString("id")).isEqualTo("ts2");
        assertThat(armed).as("the yes is written by the lane").containsExactly(0);
    }

    @Test
    @DisplayName("the yes to a configuration watch reaches the server, under the server's own id")
    void theAcceptanceIsWritten() throws Exception {
        server.says("{\"jsonrpc\":\"2.0\",\"id\":\"ts1\",\"method\":\"client/registerCapability\",\"params\":{\"registrations\":"
                + "[{\"id\":\"w\",\"method\":\"workspace/didChangeConfiguration\"}]}}");
        assertThat(readOneRound(bridge.fromServer())).isEmpty();
        bridge.work();
        List<JSONObject> got = serverGot();
        assertThat(got).hasSize(1);
        assertThat(got.get(0).getString("id")).isEqualTo("ts1");
        assertThat(got.get(0).has("result")).isTrue();
        assertThat(got.get(0).has("error")).isFalse();
    }

    @Test
    @DisplayName("the server's info-level log stays out of the IDE's; its warnings and errors are the client's to show")
    void infoLogIsKeptOut() throws Exception {
        byte[] info = PullDiagnostics.frame(("{\"jsonrpc\":\"2.0\",\"method\":\"window/logMessage\",\"params\":"
                + "{\"type\":3,\"message\":\"======== Cloning snapshot 1 ========\"}}").getBytes(StandardCharsets.UTF_8));
        byte[] log = PullDiagnostics.frame(("{\"jsonrpc\":\"2.0\",\"method\":\"window/logMessage\",\"params\":"
                + "{\"type\":4,\"message\":\"Updated watches in 17µs\"}}").getBytes(StandardCharsets.UTF_8));
        byte[] warning = PullDiagnostics.frame(("{\"jsonrpc\":\"2.0\",\"method\":\"window/logMessage\",\"params\":"
                + "{\"type\":2,\"message\":\"tsconfig.json: unknown option\"}}").getBytes(StandardCharsets.UTF_8));
        byte[] error = PullDiagnostics.frame(("{\"jsonrpc\":\"2.0\",\"method\":\"window/logMessage\",\"params\":"
                + "{\"type\":1,\"message\":\"panic\"}}").getBytes(StandardCharsets.UTF_8));
        byte[] shown = PullDiagnostics.frame(("{\"jsonrpc\":\"2.0\",\"method\":\"window/showMessage\",\"params\":"
                + "{\"type\":3,\"message\":\"said to the user, so it is the user's\"}}").getBytes(StandardCharsets.UTF_8));
        for (byte[] part : new byte[][] {info, warning, log, error, shown}) {
            server.says(part);
        }
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        for (byte[] part : new byte[][] {warning, error, shown}) {
            kept.write(part);
        }
        assertThat(bridge.fromServer().readAllBytes()).isEqualTo(kept.toByteArray());
    }

    @Test
    @DisplayName("shutdown and exit lose the null params the platform's client writes, which this server refuses")
    void shutdownAndExitLoseNullParams() throws Exception {
        clientWrites("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"initialize\",\"params\":{\"capabilities\":{}}}");
        clientWrites("{\"jsonrpc\":\"2.0\",\"id\":\"5\",\"method\":\"shutdown\",\"params\":null}");
        clientWrites("{\"jsonrpc\":\"2.0\",\"method\":\"exit\",\"params\":null}");
        List<JSONObject> got = serverGot();
        assertThat(got.get(1).getString("method")).isEqualTo("shutdown");
        assertThat(got.get(1).has("params")).as("InvalidParams: expected no params, got null (TypeScript 7.0.2)").isFalse();
        assertThat(got.get(1).getString("id")).isEqualTo("5");
        assertThat(got.get(2).getString("method")).isEqualTo("exit");
        assertThat(got.get(2).has("params")).isFalse();

        byte[] asIs = PullDiagnostics.frame("{\"jsonrpc\":\"2.0\",\"id\":\"6\",\"method\":\"shutdown\"}".getBytes(StandardCharsets.UTF_8));
        client.write(asIs);
        assertThat(toServer.toByteArray()).as("one written without params is not rewritten").isEqualTo(asIs);
    }

    @Test
    @DisplayName("an overtaken question is asked again, twice at most, and any other error is not")
    void overtakenIsAskedAgainTwice() throws Exception {
        opens(A);
        bridge.work();
        String id = questions(serverGot()).get(0)[1];
        // three overtaken answers and one refusal, queued before the client reads any of them
        Server later = server;
        later.says("{\"jsonrpc\":\"2.0\",\"id\":\"" + id + "\",\"error\":{\"code\":-32801,\"message\":\"content modified\"}}");
        InputStream in = bridge.fromServer();
        // read nothing yet: step the lane after each answer by feeding them one at a time
        armed.clear();
        assertThat(readOneRound(in)).isEmpty();
        assertThat(armed).as("asked again, after the change wait").containsExactly(PullDiagnostics.CHANGE_MILLIS);
        bridge.work();
        String second = questions(serverGot()).get(0)[1];
        later.says("{\"jsonrpc\":\"2.0\",\"id\":\"" + second + "\",\"error\":{\"code\":-32802,\"message\":\"server cancelled\"}}");
        assertThat(readOneRound(in)).isEmpty();
        bridge.work();
        String third = questions(serverGot()).get(0)[1];
        later.says("{\"jsonrpc\":\"2.0\",\"id\":\"" + third + "\",\"error\":{\"code\":-32801,\"message\":\"content modified\"}}");
        armed.clear();
        assertThat(readOneRound(in)).isEmpty();
        bridge.work();
        assertThat(questions(serverGot())).as("twice is enough: a server that always says this is not asked for ever").isEmpty();

        opens(B);
        bridge.work();
        String refused = questions(serverGot()).get(0)[1];
        server.says("{\"jsonrpc\":\"2.0\",\"id\":\"" + refused + "\",\"error\":{\"code\":-32603,\"message\":\"internal error\"}}");
        assertThat(readOneRound(in)).isEmpty();
        bridge.work();
        assertThat(questions(serverGot())).as("an error that is not 'ask again' is not asked again").isEmpty();
    }

    /** Reads what is there without reaching the end of the fake server's queue. */
    private List<JSONObject> readOneRound(InputStream in) throws IOException {
        // a marker frame after the answer: reading up to it proves the answer was consumed
        String marker = "{\"jsonrpc\":\"2.0\",\"method\":\"$/marker\"}";
        server.says(marker);
        byte[] whole = PullDiagnostics.frame(marker.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream read = new ByteArrayOutputStream();
        byte[] buf = new byte[64];
        while (!endsWith(read.toByteArray(), whole)) {
            int n = in.read(buf, 0, buf.length);
            assertThat(n).as("the marker is still to come").isPositive();
            read.write(buf, 0, n);
        }
        List<JSONObject> seen = new ArrayList<>();
        for (String body : frames(read.toByteArray())) {
            if (!body.equals(marker)) {
                seen.add(new JSONObject(body));
            }
        }
        return seen;
    }

    private static boolean endsWith(byte[] all, byte[] tail) {
        if (all.length < tail.length) {
            return false;
        }
        for (int i = 0; i < tail.length; i++) {
            if (all[all.length - tail.length + i] != tail[i]) {
                return false;
            }
        }
        return true;
    }

    @Test
    @DisplayName("an answer older than one already published for the document is dropped")
    void anOlderAnswerDoesNotOverwriteANewerOne() throws Exception {
        opens(A);
        bridge.work();
        String first = questions(serverGot()).get(0)[1];
        changes(A);
        bridge.work();
        String second = questions(serverGot()).get(0)[1];

        server.says(answer(second, "{\"kind\":\"full\",\"items\":[]}"));
        server.says(answer(first, REAL_ANSWER));
        List<JSONObject> seen = clientSees();
        assertThat(seen).as("the stale answer, arriving late, is not shown over the fresh one").hasSize(1);
        assertThat(seen.get(0).getJSONObject("params").getJSONArray("diagnostics").length()).isZero();
    }

    @Test
    @DisplayName("an answer of this class's that nothing waits for is still not the client's")
    void aForgottenAnswerIsSwallowed() throws Exception {
        server.says(answer(PullDiagnostics.ID_PREFIX + "999", REAL_ANSWER));
        assertThat(clientSees()).isEmpty();
    }

    @Test
    @DisplayName("an unchanged report publishes nothing, and a related document is published under the client's own spelling")
    void unchangedAndRelated() throws Exception {
        opens(A);
        opens(B);
        bridge.work();
        List<String[]> asked = questions(serverGot());
        String forA = asked.stream().filter(q -> q[0].equals(A)).findFirst().orElseThrow()[1];
        String forB = asked.stream().filter(q -> q[0].equals(B)).findFirst().orElseThrow()[1];

        server.says(answer(forB, "{\"kind\":\"unchanged\",\"resultId\":\"r1\"}"));
        // this server lower-cases the paths it names on a file system that ignores case (measured)
        server.says(answer(forA, "{\"kind\":\"full\",\"items\":[],\"relatedDocuments\":{\"" + B.toLowerCase(java.util.Locale.ROOT)
                + "\":{\"kind\":\"full\",\"items\":[{\"range\":{\"start\":{\"line\":3,\"character\":0},\"end\":{\"line\":3,\"character\":1}},"
                + "\"severity\":1,\"message\":\"broken by the edit\"}]}}}"));
        List<JSONObject> seen = clientSees();
        assertThat(seen).extracting(m -> m.getJSONObject("params").getString("uri")).containsExactly(A, B);
        assertThat(seen.get(1).getJSONObject("params").getJSONArray("diagnostics").length()).isEqualTo(1);
    }

    @Test
    @DisplayName("framing this cannot follow is passed through as it came, and nothing more is asked")
    void lostFramingPassesEverythingThrough() throws Exception {
        opens(A);
        serverGot();
        byte[] junk = "Content-Type: nonsense\r\n\r\n{\"not\":\"framed\"}".getBytes(StandardCharsets.US_ASCII);
        client.write(junk);
        byte[] after = "more bytes, whatever they are".getBytes(StandardCharsets.US_ASCII);
        client.write(after);
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(junk);
        expected.write(after);
        assertThat(toServer.toByteArray()).isEqualTo(expected.toByteArray());
        toServer.reset();
        bridge.work();
        assertThat(toServer.size()).as("a stream whose frames cannot be told apart gets no frame of this class's").isZero();

        connect();
        byte[] fromServer = "Garbage-Header: 1\r\n\r\nrest of the stream".getBytes(StandardCharsets.US_ASCII);
        server.says(fromServer);
        // exactly what was said, and not the end of the stream: the end stops the questions by itself
        assertThat(bridge.fromServer().readNBytes(fromServer.length)).isEqualTo(fromServer);
        opens(A);
        toServer.reset();
        bridge.work();
        assertThat(toServer.size()).as("and the server's side losing its framing stops the questions too").isZero();
    }

    @Test
    @DisplayName("bounds: a body over the limit passes unread, and a pass asks about a bounded number of documents")
    void bounds() throws Exception {
        byte[] huge = new byte[PullDiagnostics.MAX_BODY + 1];
        java.util.Arrays.fill(huge, (byte) ' ');
        byte[] marker = (PullDiagnostics.ID_PREFIX + "1").getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker, 0, huge, 10, marker.length); // would be read as this class's, were it read
        byte[] framed = PullDiagnostics.frame(huge);
        server.says(framed);
        assertThat(bridge.fromServer().readAllBytes()).isEqualTo(framed);

        connect();
        for (int i = 0; i < PullDiagnostics.MAX_OPEN + 9; i++) {
            opens("file:///p/f" + i + ".ts");
        }
        serverGot();
        armed.clear();
        bridge.work();
        assertThat(questions(serverGot())).hasSize(PullDiagnostics.MAX_BATCH);
        assertThat(armed).as("the rest are asked about in the next pass").containsExactly(PullDiagnostics.OPEN_MILLIS);
        int asked = PullDiagnostics.MAX_BATCH;
        for (int pass = 0; pass < 40; pass++) {
            bridge.work();
            asked += questions(serverGot()).size();
        }
        assertThat(asked).as("no more documents are followed than the limit").isEqualTo(PullDiagnostics.MAX_OPEN);
    }

    @Test
    @DisplayName("wiring: the launch puts this between the client and TypeScript 7's server, before the Action Items tap")
    void wiring() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java")).replace("\r\n", "\n");
        assertThat(src).contains("PullDiagnostics asks = new PullDiagnostics(serverIn, serverOut,");
        assertThat(src).contains("serverIn = asks.toServer();").contains("serverOut = asks.fromServer();");
        assertThat(src.indexOf("serverOut = asks.fromServer();"))
                .as("the tap reads what this publishes, so Action Items has it too")
                .isLessThan(src.indexOf("new DiagnosticsTap(serverOut, command.get(0))"));
        assertThat(src).as("TypeScript 7's server is launched asking").contains("launch(lookup, TsNative.command(exe), null, true)")
                .contains("launch(lookup, TsNative.command(own), null, true)");
        assertThat(src).as("and no other server is: each was measured to publish by itself, or not measured")
                .containsOnlyOnce("TsNative.command(exe), null, true").containsOnlyOnce("TsNative.command(own), null, true");
        java.util.regex.Matcher asking = java.util.regex.Pattern
                .compile("launch\\([^;]*?,\\s*(?:null|[A-Za-z]\\w*),\\s*true\\)").matcher(src);
        int count = 0;
        while (asking.find()) {
            count++;
        }
        assertThat(count).as("launches that ask for problems").isEqualTo(2);
    }

    /** Lets the bridge read one request of the server's without reaching the end of the fake server's queue. */
    private void serverAsksToBeAskedAgain(InputStream in, int id) throws IOException {
        server.says("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"workspace/diagnostic/refresh\"}");
        assertThat(readOneRound(in)).as("the request is this class's to answer").isEmpty();
    }

    @Test
    @DisplayName("a server that keeps asking to be asked again is answered within the longest wait: its requests do not push the answer away for ever")
    void aRefreshStormDoesNotStarveTheAnswer() throws Exception {
        opens(A);
        bridge.work();
        serverGot();
        armed.clear();
        InputStream in = bridge.fromServer();

        // arming the timer again moves its time: sixty milliseconds after EVERY request is never, under a storm
        serverAsksToBeAskedAgain(in, 1);
        now.addAndGet(PullDiagnostics.MAX_WAIT_MILLIS - 20);
        serverAsksToBeAskedAgain(in, 2);
        now.addAndGet(500);
        serverAsksToBeAskedAgain(in, 3);
        assertThat(armed).as("the wait is counted from the first request that has had no reply")
                .containsExactly(PullDiagnostics.OPEN_MILLIS, 20, 0);

        bridge.work();
        List<JSONObject> got = serverGot();
        assertThat(got.stream().filter(m -> m.has("result")).count()).as("every request has its reply").isEqualTo(3);
        assertThat(questions(got)).extracting(q -> q[0]).containsExactly(A);
        armed.clear();
        serverAsksToBeAskedAgain(in, 4);
        assertThat(armed).as("replied, so the wait starts over").containsExactly(PullDiagnostics.OPEN_MILLIS);
    }

    @Test
    @DisplayName("a held key with more documents open than one pass asks about does not ask on every key: the wait starts over after each pass")
    void aPassThatLeavesDocumentsWaitingStartsTheWaitOver() throws Exception {
        int docs = PullDiagnostics.MAX_BATCH + 8;
        for (int i = 0; i < docs; i++) {
            opens("file:///p/f" + i + ".ts");
        }
        bridge.work();
        bridge.work();
        serverGot();
        armed.clear();

        changes("file:///p/f0.ts");
        now.addAndGet(PullDiagnostics.MAX_WAIT_MILLIS);
        changes("file:///p/f0.ts");
        assertThat(armed).as("the longest wait is over: asked now").containsExactly(PullDiagnostics.CHANGE_MILLIS, 0);
        bridge.work();
        assertThat(questions(serverGot())).as("one pass, and documents left for the next").hasSize(PullDiagnostics.MAX_BATCH);
        armed.clear();

        // the key is still held: the next change is thirty milliseconds on, before the pass for the rest
        now.addAndGet(30);
        changes("file:///p/f0.ts");
        assertThat(armed).as("asked a moment ago, so this key waits like any other: a pass per key is "
                + PullDiagnostics.MAX_BATCH + " questions per key").containsExactly(PullDiagnostics.CHANGE_MILLIS);
    }

    @Test
    @DisplayName("a related document open under exactly the server's spelling is published under that spelling, not under a neighbour's that differs by case")
    void aRelatedDocumentKeepsItsOwnSpelling() throws Exception {
        // two files on a file system that tells case apart (Linux), both open
        String upper = "file:///home/ada/shop/src/Model.ts";
        String lower = "file:///home/ada/shop/src/model.ts";
        opens(upper);
        opens(lower);
        bridge.work();
        String forUpper = questions(serverGot()).stream().filter(q -> q[0].equals(upper)).findFirst().orElseThrow()[1];

        server.says(answer(forUpper, "{\"kind\":\"full\",\"items\":[],\"relatedDocuments\":{\"" + lower + "\":" + REAL_ANSWER + "}}"));
        List<JSONObject> seen = clientSees();
        assertThat(seen).extracting(m -> m.getJSONObject("params").getString("uri"))
                .as("model.ts's problems are model.ts's: filed under Model.ts they replace that file's own").containsExactly(upper, lower);
        assertThat(seen.get(0).getJSONObject("params").getJSONArray("diagnostics").length()).isZero();
        assertThat(seen.get(1).getJSONObject("params").getJSONArray("diagnostics").length()).isEqualTo(2);
    }

    @Test
    @DisplayName("an older answer does not overwrite a newer one through a related document either")
    void anOlderAnswerDoesNotOverwriteThroughARelatedDocument() throws Exception {
        opens(A);
        opens(B);
        bridge.work();
        String early = questions(serverGot()).stream().filter(q -> q[0].equals(A)).findFirst().orElseThrow()[1];
        changes(B);
        bridge.work();
        String late = questions(serverGot()).stream().filter(q -> q[0].equals(B)).findFirst().orElseThrow()[1];

        // B was fixed and asked about again; the answer to the earlier question about A still names B's old problems
        server.says(answer(late, "{\"kind\":\"full\",\"items\":[]}"));
        server.says(answer(early, "{\"kind\":\"full\",\"items\":[],\"relatedDocuments\":{\"" + B + "\":" + REAL_ANSWER + "}}"));
        List<JSONObject> seen = clientSees();
        assertThat(seen).extracting(m -> m.getJSONObject("params").getString("uri"))
                .as("B's fresh answer stands; the stale one for A still publishes A").containsExactly(B, A);
        assertThat(seen.get(0).getJSONObject("params").getJSONArray("diagnostics").length()).isZero();

        // and the other way round: what a later question said of B is not replaced by B's own earlier answer
        connect();
        opens(B);
        bridge.work();
        String own = questions(serverGot()).get(0)[1];
        opens(A);
        bridge.work();
        String forA = questions(serverGot()).get(0)[1];
        server.says(answer(forA, "{\"kind\":\"full\",\"items\":[],\"relatedDocuments\":{\"" + B + "\":{\"kind\":\"full\",\"items\":[]}}}"));
        server.says(answer(own, REAL_ANSWER));
        seen = clientSees();
        assertThat(seen).extracting(m -> m.getJSONObject("params").getString("uri")).containsExactly(A, B);
        assertThat(seen.get(1).getJSONObject("params").getJSONArray("diagnostics").length())
                .as("B's own answer, to the older question, arrived last and is dropped").isZero();
    }
}

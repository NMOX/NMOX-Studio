package org.nmox.studio.web3.engine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE LIVE PROOF of ledger 12, against a real {@code anvil}: subscribe over
 * WebSocket, mine, watch the heads and logs arrive in order; then CUT the
 * TCP connection under the socket (a proxy sits between them, so the drop
 * is a real one the JDK client discovers on its own) and watch the poller
 * resume with no duplicate block, no skipped block, and every log exactly
 * once. The test drives the same {@link WatchReconciler} decisions the
 * Watch pane does, lane by lane.
 *
 * <p>Skipped when {@code anvil} is not installed (CI runners have none);
 * the scripted-server tests cover the socket there.
 */
class WatchStreamAnvilLiveTest {

    /** Runtime bytecode {@code PUSH1 0 PUSH1 0 LOG0 STOP}, wrapped in a constructor returning it. */
    private static final String LOG0_INITCODE = "0x6560006000a0006000526006601af3";

    private Process anvil;
    private Proxy proxy;
    private WatchSocket socket;

    @AfterEach
    void tearDown() throws Exception {
        if (socket != null) {
            socket.close();
        }
        if (proxy != null) {
            proxy.close();
        }
        if (anvil != null) {
            anvil.destroy();
            if (!anvil.waitFor(5, TimeUnit.SECONDS)) {
                anvil.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static String anvilBinary() {
        List<String> dirs = new ArrayList<>();
        String path = System.getenv("PATH");
        if (path != null) {
            dirs.addAll(List.of(path.split(File.pathSeparator)));
        }
        dirs.add("/opt/homebrew/bin");
        dirs.add(System.getProperty("user.home") + "/.foundry/bin");
        for (String dir : dirs) {
            File candidate = new File(dir, "anvil");
            if (candidate.canExecute()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return s.getLocalPort();
        }
    }

    @Test
    @DisplayName("LIVE: heads stream in order, a cut socket falls back to polling with no dup or gap, logs arrive once")
    void streamThenFallBack() throws Exception {
        String binary = anvilBinary();
        Assumptions.assumeTrue(binary != null, "anvil is not installed");

        int port = freePort();
        anvil = new ProcessBuilder(binary, "--port", String.valueOf(port), "--host", "127.0.0.1",
                "--silent").redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        JsonRpcClient http = new JsonRpcClient("http://127.0.0.1:" + port);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (true) {
            try {
                http.chainId();
                break;
            } catch (IOException notYet) {
                assertThat(System.nanoTime()).as("anvil answers").isLessThan(deadline);
                Thread.sleep(100);
            }
        }
        String from = http.accounts().get(0);
        String deployTx = http.sendTransaction(from, null, LOG0_INITCODE, null);
        String contract = mined(http, deployTx).contractAddress();
        assertThat(contract).startsWith("0x");

        proxy = new Proxy(port);
        String wsUrl = WatchEndpoint.wsUrl(null, "http://127.0.0.1:" + proxy.port());
        assertThat(wsUrl).isEqualTo("ws://127.0.0.1:" + proxy.port());

        AtomicLong live = new AtomicLong(1);
        WatchReconciler session = new WatchReconciler(1, live::get, 50);
        BlockingQueue<Long> heads = new LinkedBlockingQueue<>();
        CountDownLatch dropped = new CountDownLatch(1);
        List<Long> fedBlocks = new CopyOnWriteArrayList<>();
        List<String> fedLogs = new CopyOnWriteArrayList<>();
        List<Long> headsSeen = new CopyOnWriteArrayList<>();

        socket = WatchSocket.open(wsUrl, List.of(contract), new WatchSocket.Events() {
            @Override
            public void onHead(long blockNumber) {
                headsSeen.add(blockNumber);
                heads.add(blockNumber);
            }

            @Override
            public void onLog(JsonRpcClient.LogEntry log, boolean removed) {
                if (session.acceptLog(WatchReconciler.Lane.STREAM, log, removed)) {
                    fedLogs.add("stream:" + log.blockNumber() + "#" + log.logIndex());
                }
            }

            @Override
            public void onDrop(String reason) {
                dropped.countDown();
            }
        }, WatchSocket.HANDSHAKE_TIMEOUT);
        assertThat(session.attach(socket)).isTrue();
        assertThat(session.startStreaming()).isTrue();

        // the seed tick, as the pane runs it right after the subscriptions confirm
        long seed = pollTick(http, session, contract, fedBlocks, fedLogs);

        for (int i = 0; i < 3; i++) {
            // one at a time: anvil batches transactions that arrive together into one block
            mined(http, http.sendTransaction(from, contract, null, null)); // one block, one LOG0
        }
        long target = seed + 3;
        long streamDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (session.lastBlock() < target || fedLogs.size() < 3) {
            assertThat(System.nanoTime()).as("the stream delivers blocks " + (seed + 1) + ".." + target
                    + " (heads=" + headsSeen + " last=" + session.lastBlock() + " logs=" + fedLogs
                    + " fed=" + fedBlocks + " chain=" + http.blockNumber() + ")")
                    .isLessThan(streamDeadline);
            Long head = heads.poll(200, TimeUnit.MILLISECONDS);
            if (head == null) {
                continue;
            }
            WatchReconciler.HeadPlan plan = session.onHead(head);
            for (long n = plan.blockFrom(); n <= plan.blockTo(); n++) {
                fedBlocks.add(http.getBlockByNumber(String.valueOf(n), false).number());
            }
            if (plan.gap()) {
                feedPolledLogs(http, session, contract, plan.blockFrom(), plan.blockTo(), fedLogs);
            }
            assertThat(session.commitHead(head)).isTrue();
        }
        assertThat(headsSeen).as("heads arrive in order").containsExactly(seed + 1, seed + 2, seed + 3);

        proxy.cut(); // the drop: TCP torn down under the client, no close frame
        assertThat(dropped.await(15, TimeUnit.SECONDS)).as("the client noticed the cut").isTrue();
        WatchReconciler.Resume resume = session.onDrop();
        assertThat(resume).isNotNull();
        assertThat(resume.lastWatchedBlock()).isEqualTo(target);
        assertThat(socket.finished()).isTrue();

        for (int i = 0; i < 2; i++) {
            mined(http, http.sendTransaction(from, contract, null, null));
        }
        long after = pollTick(http, session, contract, fedBlocks, fedLogs);
        assertThat(after).isEqualTo(target + 2);

        List<Long> expected = new ArrayList<>();
        for (long n = seed; n <= after; n++) {
            expected.add(n);
        }
        System.out.println("[live anvil] seed=" + seed + " heads=" + headsSeen
                + " resume.lastWatchedBlock=" + resume.lastWatchedBlock()
                + " fedBlocks=" + fedBlocks + " logs=" + fedLogs);
        assertThat(fedBlocks).as("every block once, in order: no duplicate, no gap")
                .containsExactlyElementsOf(expected);
        assertThat(fedLogs).as("five LOG0s, each exactly once across both lanes").hasSize(5);
        assertThat(fedLogs.stream().filter(l -> l.startsWith("stream:")).count())
                .as("the three streamed while the socket lived").isEqualTo(3);
        assertThat(fedLogs.stream().map(l -> l.substring(l.indexOf(':') + 1)).distinct().count())
                .isEqualTo(5);
    }

    private static JsonRpcClient.Receipt mined(JsonRpcClient http, String txHash) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            JsonRpcClient.Receipt receipt = http.getTransactionReceipt(txHash);
            if (receipt != null) {
                return receipt;
            }
            assertThat(System.nanoTime()).as("tx mined").isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    /** One poll tick exactly as the pane runs it; answers the head it committed. */
    private static long pollTick(JsonRpcClient http, WatchReconciler session, String contract,
            List<Long> fedBlocks, List<String> fedLogs) throws IOException {
        long head = http.blockNumber();
        WatchCursor.Plan plan = session.pollPlan(head);
        for (long n = plan.blockFrom(); n <= plan.blockTo(); n++) {
            fedBlocks.add(http.getBlockByNumber(String.valueOf(n), false).number());
        }
        if (plan.hasLogs()) {
            feedPolledLogs(http, session, contract, plan.logFrom(), plan.logTo(), fedLogs);
        }
        assertThat(session.commitPoll(plan, head, plan.hasLogs())).isTrue();
        return head;
    }

    private static void feedPolledLogs(JsonRpcClient http, WatchReconciler session, String contract,
            long from, long to, List<String> fedLogs) throws IOException {
        for (JsonRpcClient.LogEntry log : http.getLogs(contract, String.valueOf(from), String.valueOf(to))) {
            if (session.acceptLog(WatchReconciler.Lane.POLL, log, false)) {
                fedLogs.add("poll:" + log.blockNumber() + "#" + log.logIndex());
            }
        }
    }

    /** A TCP relay whose connections can be cut on demand. */
    private static final class Proxy implements AutoCloseable {

        private final ServerSocket server;
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();

        Proxy(int targetPort) throws IOException {
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket client = server.accept();
                        Socket upstream = new Socket(InetAddress.getLoopbackAddress(), targetPort);
                        sockets.add(client);
                        sockets.add(upstream);
                        pump(client, upstream);
                        pump(upstream, client);
                    } catch (IOException closed) {
                        return;
                    }
                }
            }, "anvil-proxy-accept");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() {
            return server.getLocalPort();
        }

        private static void pump(Socket from, Socket to) {
            Thread t = new Thread(() -> {
                try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                    in.transferTo(out);
                } catch (IOException cut) {
                    // the other side went away
                }
            }, "anvil-proxy-pump");
            t.setDaemon(true);
            t.start();
        }

        void cut() throws IOException {
            server.close(); // no reconnects
            for (Socket s : sockets) {
                if (s.isClosed()) {
                    continue; // cut twice: the test's cut, then teardown
                }
                try {
                    s.setSoLinger(true, 0); // RST, not a polite FIN
                } catch (IOException alreadyGone) {
                    // the pump closed it first
                }
                s.close();
            }
        }

        @Override
        public void close() throws IOException {
            cut();
        }
    }
}

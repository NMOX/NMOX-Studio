package org.nmox.studio.application;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The docs forge's Docker view (v2.164.0) exists so a developer's own
 * containers can never reach a documentation picture, and so nothing the app
 * does during a forge run can change their daemon. Both are claims about a
 * script, so this runs the script: a fake daemon on a unix socket holds one
 * docs container and one of the developer's, and the proxy must show only
 * the first and forward no write at all.
 */
@DisabledOnOs(OS.WINDOWS) // the forge runs on the developer's Mac; unix sockets and python3
class DocsDockerViewGateTest {

    private static final String CONTAINERS = "[{\"Id\":\"aaaa1111\",\"Names\":[\"/storefront-db\"],"
            + "\"Labels\":{\"org.nmox.docs\":\"1\"},\"ImageID\":\"sha256:pg\"},"
            + "{\"Id\":\"bbbb2222\",\"Names\":[\"/my-private-work\"],\"Labels\":{},\"ImageID\":\"sha256:mine\"}]";
    private static final String IMAGES = "[{\"Id\":\"sha256:pg\"},{\"Id\":\"sha256:mine\"}]";

    private final List<String> daemonSaw = new CopyOnWriteArrayList<>();
    private Path dir;
    private ServerSocketChannel daemon;
    private Process proxy;
    private int port;

    @BeforeEach
    void start() throws Exception {
        Assumptions.assumeTrue(python3(), "python3 is the forge's own prerequisite");
        dir = Files.createTempDirectory(Path.of("/tmp"), "dv");
        Path sock = dir.resolve("d.sock");
        daemon = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        daemon.bind(UnixDomainSocketAddress.of(sock));
        Thread t = new Thread(this::serve, "fake-docker-daemon");
        t.setDaemon(true);
        t.start();
        try (ServerSocket s = new ServerSocket()) {
            s.bind(new InetSocketAddress("127.0.0.1", 0));
            port = s.getLocalPort();
        }
        proxy = new ProcessBuilder("python3", Path.of("../scripts/docs-docker-proxy.py").toString(),
                String.valueOf(port), sock.toString()).redirectErrorStream(true).start();
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            try (var probe = new java.net.Socket("127.0.0.1", port)) {
                return;
            } catch (IOException notYet) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("the proxy never listened");
    }

    @AfterEach
    void stop() throws Exception {
        if (proxy != null) {
            proxy.destroyForcibly().waitFor();
        }
        if (daemon != null) {
            daemon.close();
        }
        if (dir != null) {
            try (var files = Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static boolean python3() {
        try {
            return new ProcessBuilder("python3", "--version").start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** One request per connection, answered from two fixed bodies. */
    private void serve() {
        while (daemon.isOpen()) {
            try (SocketChannel c = daemon.accept()) {
                ByteBuffer buf = ByteBuffer.allocate(8192);
                StringBuilder req = new StringBuilder();
                while (!req.toString().contains("\r\n\r\n") && c.read(buf) > 0) {
                    buf.flip();
                    req.append(StandardCharsets.UTF_8.decode(buf));
                    buf.clear();
                }
                String line = req.toString().lines().findFirst().orElse("");
                daemonSaw.add(line);
                String body = line.contains("/images/json") ? IMAGES : CONTAINERS;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                String head = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                        + bytes.length + "\r\nConnection: close\r\n\r\n";
                c.write(ByteBuffer.wrap(head.getBytes(StandardCharsets.UTF_8)));
                c.write(ByteBuffer.wrap(bytes));
            } catch (IOException closed) {
                return;
            }
        }
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("only the docs-labelled container and its image are visible")
    void showsOnlyTheDocsContainer() throws Exception {
        String containers = send("GET", "/v1.45/containers/json?all=1").body();
        assertThat(containers).contains("storefront-db").doesNotContain("my-private-work");
        String images = send("GET", "/v1.45/images/json").body();
        assertThat(images).contains("sha256:pg").doesNotContain("sha256:mine");
        assertThat(send("GET", "/v1.45/containers/my-private-work/json").statusCode()).isEqualTo(404);
        assertThat(send("GET", "/v1.45/info").statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("every write is refused before it reaches the daemon")
    void refusesEveryWrite() throws Exception {
        for (String method : List.of("POST", "DELETE", "PUT")) {
            assertThat(send(method, "/v1.45/containers/storefront-db/stop").statusCode()).as(method).isEqualTo(403);
        }
        assertThat(daemonSaw).noneMatch(l -> !l.startsWith("GET "));
    }
}

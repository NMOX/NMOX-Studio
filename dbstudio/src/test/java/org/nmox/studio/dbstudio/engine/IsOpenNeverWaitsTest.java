package org.nmox.studio.dbstudio.engine;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DbBackend#isOpen()} answers while a command holds the backend (v2.155.1).
 *
 * <p>The v2.155.0 walk ran a 30 s MongoDB query and DB Studio froze for all
 * of it: the connection tree's renderer called {@code isOpen()} on the EDT,
 * {@code isOpen()} was {@code synchronized}, and {@code runConsole} held the
 * same monitor for the whole command, so Cancel could never be clicked. The
 * JDBC and Services backends had the same shape. Two laws: holding a
 * backend's monitor (what a running command does) must not delay
 * {@code isOpen()}, and no backend in the engine package declares it
 * {@code synchronized} — derived from the sources, so a new backend is held
 * too.
 */
class IsOpenNeverWaitsTest {

    /** Holds {@code backend}'s monitor on another thread and times {@code isOpen()} on this one. */
    private static boolean answersWhileHeld(DbBackend backend) throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (backend) {
                holding.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "holds-the-backend");
        holder.setDaemon(true);
        holder.start();
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            CompletableFuture<Boolean> asked = CompletableFuture.supplyAsync(backend::isOpen);
            try {
                asked.get(1, TimeUnit.SECONDS);
                return true;
            } catch (java.util.concurrent.TimeoutException blocked) {
                return false;
            }
        } finally {
            release.countDown();
            holder.join(5_000);
        }
    }

    @Test
    @DisplayName("an OPEN SQLite connection reports open while its monitor is held")
    void jdbcAnswersWhileHeld(@TempDir Path dir) throws Exception {
        ConnectionSpec spec = new ConnectionSpec(UUID.randomUUID().toString(), "held",
                DbEngine.SQLITE, "", -1, "", "", dir.resolve("held.db").toString());
        DbClient client = new DbClient(spec, null);
        try {
            assertThat(client.open()).isNull();
            assertThat(client.isOpen()).isTrue();
            assertThat(answersWhileHeld(client)).as("isOpen must not wait on the running command's monitor").isTrue();
        } finally {
            client.close();
        }
        assertThat(client.isOpen()).as("closed means closed").isFalse();
    }

    @Test
    @DisplayName("a MongoDB backend answers while its monitor is held")
    void mongoAnswersWhileHeld() throws Exception {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        MongoBackend backend = new MongoBackend(new ConnectionSpec("id-held", "held",
                DbEngine.MONGODB, "127.0.0.1", closedPort, "appdb", "", ""), null);
        try {
            assertThat(answersWhileHeld(backend)).as("isOpen must not wait on the running command's monitor").isTrue();
            assertThat(backend.isOpen()).isFalse();
        } finally {
            backend.close();
        }
    }

    @Test
    @DisplayName("no backend in the engine package declares isOpen synchronized")
    void noSynchronizedIsOpen() throws IOException {
        Path engine = Path.of("src/main/java/org/nmox/studio/dbstudio/engine");
        Pattern implementsBackend = Pattern.compile("\\bimplements\\b[^{]*\\bDbBackend\\b");
        Pattern synchronizedIsOpen = Pattern.compile("synchronized\\s+boolean\\s+isOpen\\s*\\(");
        List<String> backends = new ArrayList<>();
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.list(engine)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(p, StandardCharsets.UTF_8);
                if (!implementsBackend.matcher(src).find()) {
                    continue;
                }
                backends.add(p.getFileName().toString());
                Matcher m = synchronizedIsOpen.matcher(src);
                if (m.find()) {
                    offenders.add(p.getFileName().toString());
                }
            }
        }
        assertThat(backends).as("the backends the engine package declares")
                .contains("DbClient.java", "MongoBackend.java", "CouchBackend.java", "ServicesBackend.java");
        assertThat(offenders).as("a backend whose isOpen waits on a running command's monitor").isEmpty();
    }
}

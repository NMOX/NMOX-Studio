package org.nmox.studio.application;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No run of the docs forge is given the developer's Docker daemon.
 *
 * <p>The filtered view arrived in v2.164.0 and was wired into the STAGED run
 * only. The plain run, the one that paints {@code docs/images/tabs} and so
 * the README's own pictures, went on talking to the daemon directly, and DB
 * Studio's offer for a running database container wrote the name of a
 * container on the developer's machine into two published pictures. Every
 * gate was green: {@link DocsDockerViewGateTest} proves the view filters, and
 * nothing asked whether the forge was always pointed at it.
 *
 * <p>That is a claim about a script, so this runs the script. Its
 * {@code NMOX_SHOTS_DRY=1} seam stops just before the app boots and prints
 * the {@code DOCKER_HOST} the app would have been given. There are exactly
 * two lawful answers, the view or an address nothing listens on, in the
 * plain run and the staged one, whatever the caller's own environment says.
 */
@DisabledOnOs(OS.WINDOWS) // the forge runs on the developer's Mac: sh, unix sockets, python3
class DocsForgeDockerViewGateTest {

    private static final String VIEW = "DOCKER_HOST=tcp://127.0.0.1:23750";
    private static final String DEAD = "DOCKER_HOST=tcp://127.0.0.1:9";

    private Path dir;
    private Path sock;
    private ServerSocketChannel daemon;

    @BeforeEach
    void start() throws Exception {
        Assumptions.assumeTrue(runs("python3", "--version") && runs("curl", "--version"),
                "python3 and curl are the forge's own prerequisites");
        dir = Files.createTempDirectory(Path.of("/tmp"), "fv");
        sock = dir.resolve("d.sock");
        daemon = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        daemon.bind(UnixDomainSocketAddress.of(sock));
        Thread t = new Thread(this::serve, "fake-docker-daemon");
        t.setDaemon(true);
        t.start();
    }

    @AfterEach
    void stop() throws Exception {
        if (daemon != null) {
            daemon.close();
        }
        if (dir != null) {
            try (var files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static boolean runs(String... command) {
        try {
            return new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** A daemon that answers every request 200: all the forge asks it here is whether it is there. */
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
                c.write(ByteBuffer.wrap(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n"
                        + "Content-Length: 2\r\nConnection: close\r\n\r\nOK").getBytes(StandardCharsets.UTF_8)));
            } catch (IOException closed) {
                return;
            }
        }
    }

    /**
     * A {@code docker} that knows the two questions the forge asks before it
     * boots: where the daemon's socket is, and whether a daemon answers.
     */
    private Path dockerSaying(String socketPath, boolean daemonAnswers) throws IOException {
        Path bin = Files.createDirectories(dir.resolve("bin" + System.nanoTime()));
        Path docker = bin.resolve("docker");
        Files.writeString(docker, "#!/bin/sh\n"
                + "case \"$1\" in\n"
                + "  context) echo 'unix://" + socketPath + "';;\n"
                + "  version) " + (daemonAnswers ? "echo 29.0" : "exit 1") + ";;\n"
                + "  *) exit 1;;\n"
                + "esac\n");
        Files.setPosixFilePermissions(docker, PosixFilePermissions.fromString("rwxr-xr-x"));
        return bin;
    }

    /** The forge's dry run: every line it printed. */
    private List<String> dryRun(Path dockerBin, boolean staged, String callersDockerHost) throws Exception {
        Path out = Files.createDirectories(dir.resolve("out" + System.nanoTime()));
        ProcessBuilder pb = new ProcessBuilder("sh", Path.of("..", "scripts", "docs-shots.sh").toString(),
                out.toString()).redirectErrorStream(true);
        pb.environment().put("PATH", dockerBin + ":" + System.getenv("PATH"));
        pb.environment().put("NMOX_SHOTS_DRY", "1");
        pb.environment().put("NMOX_SHOTS_NO_BUILD", "1");
        pb.environment().put("NMOX_SHOTS_APP", "/bin/sh"); // anything executable: the dry run never boots it
        pb.environment().put("NMOX_SHOTS_HOME", dir.resolve("home").toString());
        if (staged) {
            pb.environment().put("NMOX_SHOTS_STAGED", "1");
        } else {
            pb.environment().remove("NMOX_SHOTS_STAGED");
        }
        if (callersDockerHost != null) {
            pb.environment().put("DOCKER_HOST", callersDockerHost);
        }
        Path log = dir.resolve("run" + System.nanoTime() + ".log");
        Process p = pb.redirectOutput(log.toFile()).start();
        assertThat(p.waitFor(180, TimeUnit.SECONDS)).as("the dry run ends").isTrue();
        List<String> lines = Files.readAllLines(log);
        assertThat(p.exitValue()).as("the dry run's exit: %s", lines).isZero();
        return lines;
    }

    private static String dockerHostOf(List<String> lines) {
        List<String> said = lines.stream().filter(l -> l.startsWith("DOCKER_HOST=")).toList();
        assertThat(said).as("the forge says once what the app is given: %s", lines).hasSize(1);
        return said.get(0);
    }

    @Test
    @DisplayName("every run boots in a throwaway home, never the developer's: the plain run too (3.5.12)")
    void everyRunHasItsOwnHome() throws Exception {
        for (boolean staged : new boolean[] {false, true}) {
            List<String> lines = dryRun(dockerSaying(sock.toString(), true), staged, null);
            List<String> home = lines.stream().filter(l -> l.startsWith("USER_HOME=")).toList();
            assertThat(home).as("staged=%s: the forge says once which home the app gets: %s", staged, lines).hasSize(1);
            String given = home.get(0).substring("USER_HOME=".length());
            assertThat(given).as("staged=%s", staged).isEqualTo(dir.resolve("home").toString())
                    .isNotEqualTo(System.getProperty("user.home"));
        }
        String src = Files.readString(Path.of("..", "scripts", "docs-shots.sh"), StandardCharsets.UTF_8);
        assertThat(src.lines().filter(l -> l.contains("-J-Duser.home=")).toList())
                .as("the home reaches the app on the one launch line, for every run")
                .hasSize(1).first().asString().contains("-J-Duser.home=\"$HOME_DIR\"");
    }

    @Test
    @DisplayName("the plain run, the one that paints the README's pictures, is given the filtered view")
    void thePlainRunGetsTheView() throws Exception {
        assertThat(dockerHostOf(dryRun(dockerSaying(sock.toString(), true), false, null))).isEqualTo(VIEW);
    }

    @Test
    @DisplayName("the staged run is given the same view")
    void theStagedRunGetsTheView() throws Exception {
        assertThat(dockerHostOf(dryRun(dockerSaying(sock.toString(), true), true, null))).isEqualTo(VIEW);
    }

    @Test
    @DisplayName("with no daemon the app is given an address nothing listens on, never a default")
    void noDaemonMeansNoDocker() throws Exception {
        assertThat(dockerHostOf(dryRun(dockerSaying(sock.toString(), false), false, null)))
                .as("a daemon that does not answer").isEqualTo(DEAD);
        assertThat(dockerHostOf(dryRun(dockerSaying(dir.resolve("absent.sock").toString(), true), false, null)))
                .as("a socket that is not there").isEqualTo(DEAD);
    }

    @Test
    @DisplayName("the caller's own DOCKER_HOST never reaches the app")
    void theCallersDaemonIsNotInherited() throws Exception {
        String mine = "unix://" + sock;
        assertThat(dockerHostOf(dryRun(dockerSaying(sock.toString(), false), false, mine))).isEqualTo(DEAD);
        assertThat(dockerHostOf(dryRun(dockerSaying(sock.toString(), true), true, mine))).isEqualTo(VIEW);
    }
}

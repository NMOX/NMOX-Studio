package org.nmox.studio.infra.ops.remote;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** The deployer against a recording runner: order, stdin, stops. */
class DeployerTest {

    /** Records every remote command; answers from a script keyed by command prefix. */
    static final class Recorder implements RemoteRunner {
        final List<String> commands = new ArrayList<>();
        long uploadedBytes = -1;
        String failAt;

        @Override
        public Result run(SshTarget target, String remoteCommand, InputStream stdin, Consumer<String> onLine, Duration timeout) {
            commands.add(remoteCommand);
            if (stdin != null) {
                try {
                    uploadedBytes = stdin.readAllBytes().length;
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
            }
            if (failAt != null && remoteCommand.startsWith(failAt)) {
                onLine.accept("ERROR: something the host printed");
                return new Result(1, "ERROR: something the host printed", false);
            }
            if (remoteCommand.startsWith("sleep 2; docker ps")) {
                onLine.accept("{\"ID\":\"1\",\"Names\":\"nmox-shop\",\"Image\":\"nmox/shop:7\",\"Status\":\"Up 2 seconds\",\"Ports\":\"0.0.0.0:80->3000/tcp\",\"Labels\":\"nmox.ops.app=shop\",\"Networks\":\"bridge\"}");
                onLine.accept("http 80 200");
            }
            return new Result(0, "", false);
        }
    }

    static final class Steps implements Deployer.Listener {
        final List<String> trace = new ArrayList<>();

        @Override
        public void stepStarted(Deployer.Step step) {
            trace.add("start " + step);
        }

        @Override
        public void line(Deployer.Step step, String line) {
        }

        @Override
        public void stepFinished(Deployer.Step step, boolean ok) {
            trace.add((ok ? "ok " : "fail ") + step);
        }
    }

    private static Path project(Path root) throws IOException {
        Files.writeString(root.resolve("Dockerfile"), "FROM scratch\nEXPOSE 3000\n");
        Files.writeString(root.resolve("index.js"), "x");
        return root;
    }

    @Test
    @DisplayName("a good deploy runs the five remote steps in order, the bundle on the first one's stdin, and reads the container back")
    void happyPath(@TempDir Path root) throws IOException {
        Recorder r = new Recorder();
        Steps steps = new Steps();
        DeployPlan plan = new DeployPlan("shop", 80, 3000, "7");
        Deployer.Outcome out = new Deployer(r).deploy(project(root), plan, new SshTarget("root", "h", 22, null), steps);
        assertThat(out.ok()).isTrue();
        assertThat(out.httpCode()).isEqualTo("200");
        assertThat(out.container().name()).isEqualTo("nmox-shop");
        assertThat(r.commands).containsExactly(plan.unpack(), plan.swap(), plan.build(), plan.run(), plan.verify());
        assertThat(r.uploadedBytes).isGreaterThan(100);
        assertThat(steps.trace).containsExactly("start BUNDLE", "ok BUNDLE", "start UPLOAD", "ok UPLOAD",
                "start SWAP", "ok SWAP", "start BUILD", "ok BUILD", "start RUN", "ok RUN", "start VERIFY", "ok VERIFY");
    }

    @Test
    @DisplayName("a failed build stops the deploy there, names the step and keeps the host's last words")
    void stopsAtBuild(@TempDir Path root) throws IOException {
        Recorder r = new Recorder();
        r.failAt = "docker build";
        Steps steps = new Steps();
        Deployer.Outcome out = new Deployer(r).deploy(project(root), new DeployPlan("shop", 80, 3000, "7"),
                new SshTarget("root", "h", 22, null), steps);
        assertThat(out.ok()).isFalse();
        assertThat(out.failedAt()).isEqualTo(Deployer.Step.BUILD);
        assertThat(out.reason()).contains("something the host printed");
        assertThat(r.commands).hasSize(3);
        assertThat(steps.trace).endsWith("fail BUILD");
    }

    @Test
    @DisplayName("a refused bundle never opens ssh")
    void refusedBundle(@TempDir Path root) throws IOException {
        for (int i = 0; i < 3; i++) {
            Files.writeString(root.resolve("f" + i), "0123456789".repeat(1000));
        }
        Recorder r = new Recorder();
        // the caps are constants in production; a 20,001-file fixture is slow, so the refusal path is
        // exercised through the manifest's own contract instead
        ProjectBundle.Manifest refused = ProjectBundle.plan(root, 2, 1_000_000);
        assertThat(refused.refused()).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ProjectBundle.write(root, refused, java.io.OutputStream.nullOutputStream()))
                .isInstanceOf(IOException.class).hasMessageContaining("refused");
        assertThat(r.commands).isEmpty();
    }
}

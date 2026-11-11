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
    @DisplayName("a rollback runs exactly the run and verify lines of the earlier stamp: nothing uploaded, nothing built")
    void rollback() {
        Recorder r = new Recorder();
        Steps steps = new Steps();
        DeployPlan earlier = new DeployPlan("shop", 80, 3000, "6");
        Deployer.Outcome out = new Deployer(r).rollback(earlier, new SshTarget("root", "203.0.113.4", 22, null), steps);
        assertThat(out.ok()).isTrue();
        assertThat(r.commands).containsExactly(earlier.run(), earlier.verify());
        assertThat(r.commands.get(0)).endsWith(" nmox/shop:6");
        assertThat(r.uploadedBytes).as("no stdin on any step").isEqualTo(-1);
        assertThat(steps.trace).containsExactly("start RUN", "ok RUN", "start VERIFY", "ok VERIFY");
        assertThat(out.httpCode()).isEqualTo("200");
    }

    @Test
    @DisplayName("a rollback whose container does not start stops at run with the host's last words")
    void rollbackStops() {
        Recorder r = new Recorder();
        r.failAt = "docker rm -f";
        Steps steps = new Steps();
        Deployer.Outcome out = new Deployer(r).rollback(new DeployPlan("shop", 80, 3000, "6"),
                new SshTarget("root", "203.0.113.4", 22, null), steps);
        assertThat(out.ok()).isFalse();
        assertThat(out.failedAt()).isEqualTo(Deployer.Step.RUN);
        assertThat(out.reason()).contains("something the host printed");
        assertThat(r.commands).as("verify never ran").hasSize(1);
        assertThat(steps.trace).containsExactly("start RUN", "fail RUN");
    }

    @Test
    @DisplayName("a failed swap's reason is the last thing it printed, and none when it printed nothing")
    void swapKeepsItsLastWords(@TempDir Path root) throws IOException {
        for (String said : new String[]{"mv: cannot move 'src.next' to 'src': Permission denied", null}) {
            RemoteRunner refusing = (target, command, stdin, onLine, timeout) -> {
                if (stdin != null) {
                    try {
                        stdin.readAllBytes();
                    } catch (IOException ex) {
                        throw new AssertionError(ex);
                    }
                }
                if (command.contains("mv src.next src")) {
                    if (said != null) {
                        onLine.accept("something earlier");
                        onLine.accept(said);
                        onLine.accept("   ");
                    }
                    return new RemoteRunner.Result(1, "", false);
                }
                return new RemoteRunner.Result(0, "", false);
            };
            Deployer.Outcome out = new Deployer(refusing).deploy(project(root), new DeployPlan("shop", 80, 3000, "7"),
                    new SshTarget("root", "203.0.113.4", 22, null), new Steps());
            assertThat(out.failedAt()).isEqualTo(Deployer.Step.SWAP);
            assertThat(out.reason()).as("what the host printed last, or nothing").isEqualTo(said);
        }
    }

    @Test
    @DisplayName("a container docker lists as restarting is not up: verify fails and quotes docker's own status")
    void crashLoopIsNotUp(@TempDir Path root) throws IOException {
        RemoteRunner crashing = (target, command, stdin, onLine, timeout) -> {
            if (stdin != null) {
                try {
                    stdin.readAllBytes();
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
            }
            if (command.startsWith("sleep 2; docker ps")) {
                onLine.accept("{\"Names\":\"nmox-shop\",\"Image\":\"nmox/shop:7\",\"Status\":\"Restarting (1) 2 seconds ago\",\"Ports\":\"\",\"Labels\":\"nmox.ops.app=shop\",\"Networks\":\"bridge\"}");
                onLine.accept("http 80 none");
            }
            return new RemoteRunner.Result(0, "", false);
        };
        Steps steps = new Steps();
        Deployer.Outcome out = new Deployer(crashing).deploy(project(root), new DeployPlan("shop", 80, 3000, "7"),
                new SshTarget("root", "203.0.113.4", 22, null), steps);
        assertThat(out.ok()).isFalse();
        assertThat(out.failedAt()).isEqualTo(Deployer.Step.VERIFY);
        // the reason is the container as docker listed it, not a sentence built here: the Hub words it
        assertThat(out.reason()).isNull();
        assertThat(out.container().status()).isEqualTo("Restarting (1) 2 seconds ago");
        assertThat(steps.trace).endsWith("fail VERIFY");
    }

    @Test
    @DisplayName("an upload the host hung up on is explained in the host's words, not as a closed pipe")
    void uploadFailureUsesTheHostsWords(@TempDir Path root) throws IOException {
        byte[] noise = new byte[1_500_000];
        new java.util.Random(7).nextBytes(noise); // incompressible, and larger than the pipe between tar and ssh
        Files.write(project(root).resolve("big.bin"), noise);
        RemoteRunner hangsUp = (target, command, stdin, onLine, timeout) -> {
            try {
                stdin.close(); // the remote tar died at once: nothing reads the pipe
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
            onLine.accept("mkdir: cannot create directory '/srv/nmox': Permission denied");
            return new RemoteRunner.Result(1, "mkdir: cannot create directory '/srv/nmox': Permission denied", false);
        };
        Deployer.Outcome out = new Deployer(hangsUp).deploy(root, new DeployPlan("shop", 80, 3000, "7"),
                new SshTarget("deploy", "203.0.113.4", 22, null), new Steps());
        assertThat(out.failedAt()).isEqualTo(Deployer.Step.UPLOAD);
        assertThat(out.reason()).isEqualTo("mkdir: cannot create directory '/srv/nmox': Permission denied");
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

    @Test
    @DisplayName("a refused bundle is an outcome of data: its kind and figure, no sentence, and the log keeps its own line and notes")
    void refusedBundleIsData(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("Dockerfile"), "FROM scratch\n");
        Files.writeString(root.resolve(".dockerignore"), "*.md\n!README.md\n");
        for (int i = 0; i < 4; i++) {
            Files.writeString(root.resolve("f" + i), "0123456789");
        }
        Recorder r = new Recorder();
        List<String> log = new ArrayList<>();
        Deployer.Listener listener = new Deployer.Listener() {
            public void stepStarted(Deployer.Step step) { }
            public void line(Deployer.Step step, String line) { log.add(line); }
            public void stepFinished(Deployer.Step step, boolean ok) { }
        };
        Deployer.Outcome out = new Deployer(r).deploy(root, new DeployPlan("shop", 80, 3000, "7"),
                new SshTarget("root", "203.0.113.4", 22, null), listener, 3, 1_000_000);
        assertThat(out.failedAt()).isEqualTo(Deployer.Step.BUNDLE);
        assertThat(out.reason()).as("no English sentence travels as the reason").isNull();
        assertThat(out.refused()).isEqualTo(new ProjectBundle.Refusal(ProjectBundle.Refusal.Kind.TOO_MANY_FILES, 3, null));
        assertThat(log).contains("refused: more than 3 files");
        assertThat(r.commands).as("nothing was opened").isEmpty();
        // and a bundle that goes says what it did not apply before it lists what stayed home
        log.clear();
        new Deployer(r).deploy(root, new DeployPlan("shop", 80, 3000, "7"), new SshTarget("root", "203.0.113.4", 22, null), listener);
        assertThat(log.get(0)).contains(".dockerignore has exception lines");
    }

    @Test
    @DisplayName("the verify line's status is a real HTTP status or none: curl's 000 for no answer is not a status")
    void httpCodeIsAStatusOrNone() {
        assertThat(Deployer.httpCode("http 80 200")).isEqualTo("200");
        assertThat(Deployer.httpCode("http 8080 503")).isEqualTo("503");
        assertThat(Deployer.httpCode("http 80 000")).as("curl's word for no answer").isEqualTo("none");
        assertThat(Deployer.httpCode("http 80 000none")).as("what 3.11.0's line printed on a refused connection").isEqualTo("none");
        assertThat(Deployer.httpCode("http 80 none")).isEqualTo("none");
        assertThat(Deployer.httpCode("http 80")).as("no curl on the host").isEqualTo("none");
        assertThat(Deployer.httpCode("http 80 2000")).isEqualTo("none");
        // and the line that runs: curl's own output alone, with a word for the case where there is none
        assertThat(new DeployPlan("shop", 80, 3000, "1").verify()).contains("echo http 80 ${code:-none}").doesNotContain("|| echo none");
    }
}

package org.nmox.studio.application;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Dockerize proof keeps its laws (3.13.0).
 *
 * <p>{@code scripts/dockerize-proof.sh} builds and runs every container
 * recipe the product generates, on a runner, because "a Dockerfile that was
 * never built is a claim" (v1.353.0) and the machines this product is written
 * on do not always run a daemon. A script that drives somebody's Docker has
 * laws of its own, and they are the ones held here:
 *
 * <ul>
 * <li>it removes what it made and nothing else: containers and images by the
 *     label of its own run, a compose stack by the project name of its own
 *     run, and it never prunes;</li>
 * <li>with no daemon it refuses by name, with an exit code of its own, so a
 *     machine without Docker cannot report a proof;</li>
 * <li>every recipe gets one verdict line in a fixed form, and one recipe that
 *     does not answer fails the run by name;</li>
 * <li>the workflow runs on demand, weekly and on a {@code proof/**} push,
 *     never on a pull request, with read-only permissions, and the release
 *     does not wait for it.</li>
 * </ul>
 *
 * <p>The script is RUN, against a stand-in {@code docker} that writes down
 * every command it is given and already "holds" a container and an image
 * that are not the proof's (the {@code WalkSeesNoDockerGateTest} shape). A
 * stand-in cannot say whether a Dockerfile builds; that is what the runner is
 * for. It can say what the script asks a daemon to remove.
 */
@DisabledOnOs(OS.WINDOWS) // the script and its stand-in docker are POSIX shell; the proof runs on a Linux runner
class DockerizeProofGateTest {

    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();
    private static final Path SCRIPT = REPO.resolve(Path.of("scripts", "dockerize-proof.sh"));
    private static final Path WORKFLOWS = REPO.resolve(Path.of(".github", "workflows"));
    private static final Path WORKFLOW = WORKFLOWS.resolve("dockerize-proof.yml");
    private static final Path GENERATOR_TEST = REPO.resolve(Path.of("rack", "src", "test", "java", "org", "nmox",
            "studio", "rack", "docker", "DockerizeProofFixturesTest.java"));

    private static final String NOT_OURS_CONTAINER = "someones-postgres";
    private static final String NOT_OURS_IMAGE = "someones-image";

    private static final Pattern VERDICT = Pattern.compile(
            "PROOF ([a-z0-9-]+): build=(\\S+) run=(\\S+) http=(\\S+) deep=(\\S+) seconds=(\\d+)");

    private HttpServer answering;

    @BeforeEach
    void startWhatTheContainersWouldServe() throws IOException {
        // what a recipe's container would answer: 200, except under /missing
        answering = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        answering.createContext("/", exchange -> {
            byte[] body = "ok\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(exchange.getRequestURI().getPath().startsWith("/missing") ? 404 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        answering.start();
    }

    @AfterEach
    void stop() {
        answering.stop(0);
    }

    /** What one run of the script did: its exit code, what it printed, and every docker command it gave. */
    private record Ran(int exit, String output, List<String> docker, List<String> removed) {

        List<String> verdicts() {
            return output.lines().filter(l -> l.startsWith("PROOF ") && l.contains(": build=")).toList();
        }

        List<String> commands(String verb) {
            return docker.stream().filter(c -> c.equals(verb) || c.startsWith(verb + " ")).toList();
        }
    }

    /**
     * Runs the script over {@code proofList} with a stand-in docker first on
     * PATH. {@code info} is the exit code its {@code docker info} gives;
     * a build whose project folder is {@code failingBuild} fails.
     */
    private Ran run(Path tmp, List<String> proofList, int info, String failingBuild) throws Exception {
        Path out = Files.createDirectories(tmp.resolve("out"));
        Files.write(out.resolve("proof.list"), proofList, StandardCharsets.UTF_8);
        for (String line : proofList) {
            Files.createDirectories(out.resolve(line.split(" ")[1]));
        }
        Path state = Files.createDirectories(tmp.resolve("state"));
        Files.writeString(state.resolve("containers"), NOT_OURS_CONTAINER + "\n", StandardCharsets.UTF_8);
        Files.writeString(state.resolve("images"), NOT_OURS_IMAGE + "\n", StandardCharsets.UTF_8);
        Path bin = Files.createDirectories(tmp.resolve("bin"));
        Path docker = bin.resolve("docker");
        Files.writeString(docker, String.join("\n",
                "#!/bin/sh",
                "printf '%s\\n' \"$*\" >> \"$FAKE_STATE/commands\"",
                "labelled() { case \"$*\" in *'--filter label=org.nmox.dockerize-proof='*) return 0;; esac; return 1; }",
                "next() { n=$(cat \"$FAKE_STATE/n\" 2>/dev/null || echo 0); n=$((n + 1)); echo \"$n\" > \"$FAKE_STATE/n\"; }",
                "forget() { f=\"$FAKE_STATE/$1\"; shift; for id in \"$@\"; do case \"$id\" in -*) continue;; esac;",
                "  echo \"$id\" >> \"$FAKE_STATE/removed\"; grep -v -x \"$id\" \"$f\" > \"$f.new\"; mv \"$f.new\" \"$f\"; done; }",
                "case \"$1\" in",
                "  info) exit \"$FAKE_INFO\" ;;",
                "  version) echo 'stand-in docker' ;;",
                "  build) for last in \"$@\"; do :; done",
                "    case \"$last\" in */\"$FAKE_FAILING_BUILD\") echo 'the build failed'; exit 1;; esac",
                "    next; echo \"image$n\" >> \"$FAKE_STATE/images\" ;;",
                "  run) next; echo \"container$n\" >> \"$FAKE_STATE/containers\"; echo \"container$n\" ;;",
                "  port) echo \"127.0.0.1:$FAKE_PORT\" ;;",
                "  inspect) echo true ;;",
                // a daemon asked WITHOUT the label answers with everything it holds, the neighbour's included
                "  ps) if labelled \"$@\"; then grep -v -x '" + NOT_OURS_CONTAINER + "' \"$FAKE_STATE/containers\"; else cat \"$FAKE_STATE/containers\"; fi ;;",
                "  images) if labelled \"$@\"; then grep -v -x '" + NOT_OURS_IMAGE + "' \"$FAKE_STATE/images\"; else cat \"$FAKE_STATE/images\"; fi ;;",
                "  rm) shift; forget containers \"$@\" ;;",
                "  rmi) shift; forget images \"$@\" ;;",
                "esac",
                "exit 0",
                ""), StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(docker, PosixFilePermissions.fromString("rwxr-xr-x"));

        ProcessBuilder pb = new ProcessBuilder("/bin/sh", SCRIPT.toString(), out.toString())
                .directory(REPO.toFile())
                .redirectErrorStream(true)
                .redirectOutput(tmp.resolve("script-output.txt").toFile());
        Map<String, String> env = pb.environment();
        env.put("PATH", bin + File.pathSeparator + env.get("PATH"));
        env.put("FAKE_STATE", state.toString());
        env.put("FAKE_INFO", String.valueOf(info));
        env.put("FAKE_PORT", String.valueOf(answering.getAddress().getPort()));
        env.put("FAKE_FAILING_BUILD", failingBuild == null ? "@none@" : failingBuild);
        env.put("NMOX_PROOF_HTTP_WAIT", "2");
        env.remove("NMOX_PROOF_ONLY");
        Process script = pb.start();
        try {
            assertThat(script.waitFor(120, TimeUnit.SECONDS)).as("the proof of a stand-in comes back").isTrue();
        } finally {
            script.descendants().forEach(ProcessHandle::destroyForcibly);
            script.destroyForcibly();
        }
        return new Ran(script.exitValue(),
                Files.readString(tmp.resolve("script-output.txt"), StandardCharsets.UTF_8),
                lines(state.resolve("commands")), lines(state.resolve("removed")));
    }

    private static List<String> lines(Path file) throws IOException {
        return Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
    }

    /** One recipe of each way of proving, all answered by the stand-in. */
    private List<String> everyWay() {
        int port = answering.getAddress().getPort();
        return List.of(
                "server proof-server image 8080 -",
                "spa proof-spa image 80 /some/client/route",
                "stack proof-stack compose " + port + " /api/health",
                "generic proof-generic idle - -");
    }

    @Test
    @DisplayName("it removes the containers and images of its own run, found by its own label, and never a neighbour's")
    void removesOnlyWhatItLabelled(@TempDir Path tmp) throws Exception {
        Ran ran = run(tmp, everyWay(), 0, null);
        assertThat(ran.exit()).as(ran.output()).isZero();

        List<String> builds = ran.commands("build");
        List<String> runs = ran.commands("run");
        assertThat(builds).as("the three image recipes were built").hasSize(3);
        assertThat(runs).as("and run").hasSize(3);
        TreeSet<String> labels = new TreeSet<>();
        Pattern label = Pattern.compile("--label (org\\.nmox\\.dockerize-proof=\\S+)");
        for (String made : Stream.concat(builds.stream(), runs.stream()).toList()) {
            Matcher m = label.matcher(made);
            assertThat(m.find()).as("everything the proof makes carries the label of its run: docker %s", made).isTrue();
            labels.add(m.group(1));
        }
        assertThat(labels).as("one run, one label").hasSize(1);
        String filter = "--filter label=" + labels.first();
        assertThat(Stream.concat(ran.commands("ps").stream(), ran.commands("images").stream()))
                .as("the daemon is only ever asked for what carries that label: asked for everything, "
                        + "it answers with the neighbours' containers too")
                .isNotEmpty().allSatisfy(listing -> assertThat(listing).contains(filter));

        assertThat(ran.removed()).as("what it made is gone: three containers, three images")
                .containsExactlyInAnyOrder("container2", "container4", "container6", "image1", "image3", "image5");
        assertThat(ran.removed()).as("and what it did not make is still there")
                .doesNotContain(NOT_OURS_CONTAINER, NOT_OURS_IMAGE);
        assertThat(ran.commands("rm")).as("containers go with their anonymous volumes")
                .isNotEmpty().allSatisfy(rm -> assertThat(rm).startsWith("rm -fv "));
        assertThat(ran.output()).contains("PROOF cleanup: nothing of this run is left");
    }

    @Test
    @DisplayName("a compose stack is brought up and taken down under the project name of this run, volumes and built images included")
    void aStackIsTakenDownByItsOwnName(@TempDir Path tmp) throws Exception {
        Ran ran = run(tmp, everyWay(), 0, null);
        List<String> compose = ran.commands("compose");
        assertThat(compose).as("config, build, up, ps, logs, down").hasSizeGreaterThanOrEqualTo(6);
        TreeSet<String> projects = new TreeSet<>();
        Pattern project = Pattern.compile("^compose -p (nmoxproof-\\S+-stack) ");
        for (String c : compose) {
            Matcher m = project.matcher(c);
            assertThat(m.find()).as("every compose command names this run's project: docker %s", c).isTrue();
            projects.add(m.group(1));
        }
        assertThat(projects).hasSize(1);
        assertThat(compose).as("the stack as written is checked, built and started")
                .anySatisfy(c -> assertThat(c).endsWith(" config -q"))
                .anySatisfy(c -> assertThat(c).endsWith(" build"))
                .anySatisfy(c -> assertThat(c).endsWith(" up -d"));
        assertThat(compose.get(compose.size() - 1)).as("and the last thing said to it takes it down whole")
                .isEqualTo("compose -p " + projects.first() + " down -v --rmi local --remove-orphans");
    }

    @Test
    @DisplayName("it never prunes: not in a run, and not anywhere in the script")
    void neverPrunes(@TempDir Path tmp) throws Exception {
        Ran ran = run(tmp, everyWay(), 0, null);
        assertThat(ran.docker()).as("the commands of a whole run").isNotEmpty()
                .noneSatisfy(c -> assertThat(c).contains("prune"));
        String code = GateSources.stripHashComments(Files.readString(SCRIPT, StandardCharsets.UTF_8));
        assertThat(code).as("the script itself, its comments set aside").contains("docker rm -fv")
                .doesNotContain("prune")
                .doesNotContain("rmi -f $(")
                .doesNotContain("--all");
        assertThat(code.lines().filter(l -> l.contains("docker ps") || l.contains("docker images")))
                .as("every listing in the script is a listing by label")
                .isNotEmpty().allSatisfy(l -> assertThat(l).contains("--filter \"label=$LABEL\""));
    }

    @Test
    @DisplayName("with no daemon it refuses by name and exits 3, having built nothing")
    void refusesWithoutADaemon(@TempDir Path tmp) throws Exception {
        Ran ran = run(tmp.resolve("down"), everyWay(), 1, null);
        assertThat(ran.exit()).as("its own exit code, so a caller can tell 'no daemon' from 'a recipe failed'").isEqualTo(3);
        assertThat(ran.output()).contains("docker is not running; this proof needs a daemon");
        assertThat(ran.docker()).as("it asked whether a daemon answers, and nothing else").containsExactly("info");
        assertThat(ran.verdicts()).as("a machine without Docker reports no proof").isEmpty();

        // and with no docker command at all: the same sentence, the same code
        Path bare = Files.createDirectories(tmp.resolve("bare/bin"));
        Path curl = bare.resolve("curl");
        Files.writeString(curl, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(curl, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path out = Files.createDirectories(tmp.resolve("bare/out"));
        Files.writeString(out.resolve("proof.list"), "server proof-server image 8080 -\n", StandardCharsets.UTF_8);
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", SCRIPT.toString(), out.toString())
                .directory(REPO.toFile()).redirectErrorStream(true)
                .redirectOutput(tmp.resolve("bare/output.txt").toFile());
        pb.environment().put("PATH", bare.toString());
        Process script = pb.start();
        assertThat(script.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(script.exitValue()).isEqualTo(3);
        assertThat(Files.readString(tmp.resolve("bare/output.txt"), StandardCharsets.UTF_8))
                .contains("docker is not running; this proof needs a daemon");
    }

    @Test
    @DisplayName("every recipe gets one verdict line in the fixed form, and a run where all of them answer exits 0")
    void oneVerdictLinePerRecipe(@TempDir Path tmp) throws Exception {
        Ran ran = run(tmp, everyWay(), 0, null);
        assertThat(ran.exit()).as(ran.output()).isZero();
        assertThat(ran.verdicts()).hasSize(4).allSatisfy(v -> assertThat(v).matches(VERDICT));
        assertThat(ran.verdicts().stream().map(v -> v.replaceFirst(" seconds=\\d+$", ""))).containsExactly(
                "PROOF server: build=0 run=0 http=200 deep=n/a",
                "PROOF spa: build=0 run=0 http=200 deep=200",
                "PROOF stack: build=0 run=0 http=200 deep=200",
                "PROOF generic: build=0 run=0 http=n/a deep=n/a");
        assertThat(Files.readAllLines(tmp.resolve("out/verdicts.txt"), StandardCharsets.UTF_8))
                .as("the same lines, kept beside the projects for the artifact").isEqualTo(ran.verdicts());
        assertThat(ran.output()).contains("dockerize-proof: PASS - 4 recipes");
    }

    @Test
    @DisplayName("a recipe that does not build, one that does not answer and one whose deep link is a 404 each fail the run by name, and what was made is still removed")
    void oneFailingRecipeFailsTheRun(@TempDir Path tmp) throws Exception {
        Ran ran = run(tmp, List.of(
                "server proof-server image 8080 -",
                "broken proof-broken image 8080 -",
                "spa proof-spa image 80 /missing/route",
                "silent proof-silent compose 9 -"), 0, "proof-broken");
        assertThat(ran.exit()).as(ran.output()).isEqualTo(1);
        assertThat(ran.verdicts().stream().map(v -> v.replaceFirst(" seconds=\\d+$", ""))).containsExactly(
                "PROOF server: build=0 run=0 http=200 deep=n/a",
                "PROOF broken: build=1 run=- http=- deep=n/a",
                "PROOF spa: build=0 run=0 http=200 deep=404",
                "PROOF silent: build=0 run=0 http=000 deep=n/a");
        assertThat(ran.output()).contains("dockerize-proof: FAIL - broken spa silent (4 proven")
                .as("and the failing build's own words reach the job log").contains("the build failed");
        assertThat(ran.removed()).as("the two that were built and run are removed all the same")
                .hasSize(4).doesNotContain(NOT_OURS_CONTAINER, NOT_OURS_IMAGE);
        assertThat(ran.commands("compose").get(ran.commands("compose").size() - 1)).contains(" down -v ");
    }

    @Test
    @DisplayName("the first 200 is waited for at most 30 seconds, whatever the environment asks")
    void theWaitIsBounded() throws Exception {
        String code = GateSources.stripHashComments(Files.readString(SCRIPT, StandardCharsets.UTF_8));
        assertThat(code).contains("HTTP_WAIT=\"${NMOX_PROOF_HTTP_WAIT:-30}\"")
                .contains("[ \"$HTTP_WAIT\" -gt 30 ] && HTTP_WAIT=30");
        assertThat(code).as("no pipe may hide a docker command's exit code").doesNotContain("| tail").doesNotContain("| tee");
    }

    // ------------------------------------------------------------ the workflow

    private static String workflow() throws IOException {
        return GateSources.stripHashComments(Files.readString(WORKFLOW, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("the workflow runs on demand, weekly and on a proof/** push, and never on a pull request")
    void theWorkflowsThreeTriggers() throws Exception {
        String wf = workflow();
        String on = wf.substring(wf.indexOf("\non:\n"), wf.indexOf("\npermissions:"));
        assertThat(on).contains("  workflow_dispatch:\n")
                .contains("  push:\n    branches: ['proof/**']\n")
                .containsPattern("  schedule:\n    - cron: \"\\d+ \\d+ \\* \\* [0-6]\"\n");
        assertThat(on).as("a check that runs on pull requests can be made required; this one is informational")
                .doesNotContain("pull_request").doesNotContain("workflow_run").doesNotContain("workflow_call")
                .doesNotContain("main");
    }

    @Test
    @DisplayName("the workflow may only read the repository, runs the one script over what the generator test wrote, and keeps the result even when it is red")
    void theWorkflowRunsTheScript() throws Exception {
        String wf = workflow();
        assertThat(wf).contains("\npermissions:\n  contents: read\n");
        assertThat(wf.lines().filter(l -> l.stripLeading().startsWith("permissions:"))).as("one grant, at the top").hasSize(1);
        assertThat(wf).doesNotContain("secrets.").doesNotContain("continue-on-error");
        assertThat(wf).contains("runs-on: ubuntu-latest").containsPattern("timeout-minutes: \\d+");

        String property = "nmox.dockerize.out";
        assertThat(Files.readString(GENERATOR_TEST, StandardCharsets.UTF_8))
                .as("the generation step's switch, in the test that reads it")
                .contains("OUT_PROPERTY = \"" + property + "\"");
        assertThat(wf).as("the generation step: one test class of the rack module, no static analysis")
                .contains("mvn -B -pl rack -am test -Dtest=DockerizeProofFixturesTest -D" + property
                        + "=\"$RUNNER_TEMP/dockerize-proof\" -Dsurefire.failIfNoSpecifiedTests=false"
                        + " -Dspotbugs.skip=true -Djacoco.skip=true");
        assertThat(wf).as("the script, not a copy of it, and nothing after it on the line to swallow its exit code")
                .contains("        run: scripts/dockerize-proof.sh \"$RUNNER_TEMP/dockerize-proof\"\n");
        int upload = wf.indexOf("uses: actions/upload-artifact@");
        assertThat(upload).isPositive();
        assertThat(wf.substring(wf.lastIndexOf("      - name:", upload), upload))
                .as("the projects, verdicts and logs are kept when the proof is red: that is when they are read")
                .contains("if: always()");
        assertThat(wf.substring(upload)).contains("path: ${{ runner.temp }}/dockerize-proof/")
                .as("a Dockerfile's neighbours are .dockerignore files").contains("include-hidden-files: true");
    }

    @Test
    @DisplayName("the workflow sets up the same checkout and the same JDK as the build")
    void theWorkflowBuildsAsTheBuildDoes() throws Exception {
        String build = GateSources.stripHashComments(
                Files.readString(WORKFLOWS.resolve("build-and-test.yml"), StandardCharsets.UTF_8));
        String wf = workflow();
        for (String same : List.of("uses: actions/checkout@(\\S+)", "uses: actions/setup-java@(\\S+)",
                "distribution: (\\S+)", "java-version: (\\S+)")) {
            assertThat(all(wf, same)).as("%s, as in build-and-test.yml", same)
                    .isNotEmpty().isEqualTo(all(build, same));
        }
        assertThat(all(wf, "uses: actions/upload-artifact@(\\S+)")).as("and the upload action the other proofs use")
                .isEqualTo(all(GateSources.stripHashComments(
                        Files.readString(WORKFLOWS.resolve("update-gauntlet.yml"), StandardCharsets.UTF_8)),
                        "uses: actions/upload-artifact@(\\S+)"));
    }

    @Test
    @DisplayName("nothing waits for the proof: no other workflow names it, the release least of all")
    void theReleaseDoesNotWaitForIt() throws Exception {
        List<String> naming = new ArrayList<>();
        try (Stream<Path> files = Files.list(WORKFLOWS)) {
            for (Path file : files.sorted().toList()) {
                if (file.equals(WORKFLOW)) {
                    continue;
                }
                String other = GateSources.stripHashComments(Files.readString(file, StandardCharsets.UTF_8))
                        .toLowerCase(java.util.Locale.ROOT);
                if (other.contains("dockerize-proof") || other.contains("dockerize proof")) {
                    naming.add(file.getFileName().toString());
                }
            }
        }
        assertThat(WORKFLOWS.resolve("release.yml")).as("the release workflow this law is about").isRegularFile();
        assertThat(naming).as("workflows that name the Dockerize proof; it is informational, and a release "
                + "that waits for a weekly proof of base images ships when Docker Hub lets it").isEmpty();
        for (String script : List.of("ship-gate.sh", "ship-branch.sh", "post-ship.sh")) {
            assertThat(GateSources.stripHashComments(
                    Files.readString(REPO.resolve(Path.of("scripts", script)), StandardCharsets.UTF_8)))
                    .as("%s does not wait for it either", script).doesNotContain("dockerize-proof");
        }
    }

    private static TreeSet<String> all(String text, String regex) {
        TreeSet<String> found = new TreeSet<>();
        Matcher m = Pattern.compile(regex).matcher(text);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }
}

package org.nmox.studio.infra.ops.remote;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.nmox.studio.core.util.Threads;

/**
 * Runs a {@link DeployPlan} against a host, one step at a time, stopping
 * at the first failure with the step named. The bundle is streamed
 * straight from the project into ssh's stdin — no temporary file on
 * either side — and every output line reaches the listener as it arrives
 * so the HUD shows the build as the build happens.
 */
public final class Deployer {

    /** The steps, in order; the listener hears each begin and end. */
    public enum Step { BUNDLE, UPLOAD, SWAP, BUILD, RUN, VERIFY }

    public interface Listener {
        void stepStarted(Step step);

        void line(Step step, String line);

        void stepFinished(Step step, boolean ok);
    }

    /** The outcome: the step that failed (null when none), the published port's answer, and docker's view of the container. */
    public record Outcome(Step failedAt, String httpCode, HostFacts.ContainerFact container, String reason) {

        public boolean ok() {
            return failedAt == null;
        }
    }

    static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(10);
    static final Duration BUILD_TIMEOUT = Duration.ofMinutes(20);
    static final Duration SHORT_TIMEOUT = Duration.ofSeconds(60);

    private final RemoteRunner runner;

    public Deployer(RemoteRunner runner) {
        this.runner = runner;
    }

    public Outcome deploy(Path project, DeployPlan plan, SshTarget target, Listener listener) {
        // BUNDLE: decide what travels before a byte moves
        listener.stepStarted(Step.BUNDLE);
        ProjectBundle.Manifest manifest;
        try {
            manifest = ProjectBundle.plan(project);
        } catch (IOException cannotRead) {
            listener.line(Step.BUNDLE, "cannot read the project: " + cannotRead.getMessage());
            listener.stepFinished(Step.BUNDLE, false);
            return new Outcome(Step.BUNDLE, null, null, cannotRead.getMessage());
        }
        if (manifest.refused()) {
            listener.line(Step.BUNDLE, "refused: " + manifest.refusal());
            listener.stepFinished(Step.BUNDLE, false);
            return new Outcome(Step.BUNDLE, null, null, manifest.refusal());
        }
        for (String s : manifest.skipped()) {
            listener.line(Step.BUNDLE, "skipped " + s);
        }
        listener.line(Step.BUNDLE, manifest.files().size() + " files, " + (manifest.bytes() / 1024) + " KB");
        listener.stepFinished(Step.BUNDLE, true);

        // UPLOAD: the tar is written on its own thread into ssh's stdin
        listener.stepStarted(Step.UPLOAD);
        PipedInputStream toSsh = new PipedInputStream(256 * 1024);
        PipedOutputStream fromTar;
        try {
            fromTar = new PipedOutputStream(toSsh);
        } catch (IOException impossible) {
            listener.stepFinished(Step.UPLOAD, false);
            return new Outcome(Step.UPLOAD, null, null, impossible.getMessage());
        }
        String[] tarProblem = {null};
        Thread writer = Threads.startDaemon(() -> {
            try (fromTar) {
                ProjectBundle.write(project, manifest, fromTar);
            } catch (IOException ex) {
                tarProblem[0] = ex.getMessage();
            }
        }, "nmox-ops-bundle");
        RemoteRunner.Result up = runner.run(target, plan.unpack(), toSsh,
                line -> listener.line(Step.UPLOAD, line), UPLOAD_TIMEOUT);
        try {
            writer.join(5_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (tarProblem[0] != null) {
            listener.line(Step.UPLOAD, "bundle failed: " + tarProblem[0]);
        }
        if (!up.ok() || tarProblem[0] != null) {
            listener.stepFinished(Step.UPLOAD, false);
            // when the REMOTE side failed, its words are the reason: the local writer's "Pipe closed"
            // is only the echo of the host hanging up (a full disk, no permission under /srv)
            String remoteSaid = up.ok() || up.tail() == null || up.tail().isBlank() ? null : lastLine(up.tail());
            return new Outcome(Step.UPLOAD, null, null, remoteSaid != null ? remoteSaid : tarProblem[0] != null ? tarProblem[0] : up.tail());
        }
        listener.stepFinished(Step.UPLOAD, true);

        if (!step(Step.SWAP, plan.swap(), target, listener, SHORT_TIMEOUT)) {
            return new Outcome(Step.SWAP, null, null, "swap failed");
        }
        // a build prints for twenty minutes: the reason for a failure is its LAST words, so only those are kept
        String[] buildLast = {null};
        if (!step(Step.BUILD, plan.build(), target, l -> {
            listener.line(Step.BUILD, l);
            if (!l.isBlank()) {
                buildLast[0] = l;
            }
        }, listener, BUILD_TIMEOUT)) {
            return new Outcome(Step.BUILD, null, null, buildLast[0] == null ? "failed" : buildLast[0]);
        }
        return runAndVerify(plan, target, listener);
    }

    /** RUN then VERIFY: replace the container by name, then read back what docker runs. */
    private Outcome runAndVerify(DeployPlan plan, SshTarget target, Listener listener) {
        String[] runLast = {null};
        if (!step(Step.RUN, plan.run(), target, l -> {
            listener.line(Step.RUN, l);
            if (!l.isBlank()) {
                runLast[0] = l;
            }
        }, listener, SHORT_TIMEOUT)) {
            return new Outcome(Step.RUN, null, null, runLast[0] == null ? "failed" : runLast[0]);
        }

        listener.stepStarted(Step.VERIFY);
        HostFacts.ContainerFact[] seen = {null};
        String[] http = {null};
        RemoteRunner.Result v = runner.run(target, plan.verify(), null, line -> {
            listener.line(Step.VERIFY, line);
            if (line.startsWith("{")) {
                HostFacts.ContainerFact c = HostProbe.parseContainer(line);
                if (c != null) {
                    seen[0] = c;
                }
            } else if (line.startsWith("http ")) {
                http[0] = httpCode(line);
            }
        }, SHORT_TIMEOUT);
        // docker lists a crash-looping container too ("Restarting (1) 2 seconds ago"): only "Up …" is running
        boolean listed = v.ok() && seen[0] != null;
        boolean running = listed && seen[0].status().startsWith("Up");
        listener.stepFinished(Step.VERIFY, running);
        if (!running) {
            return new Outcome(Step.VERIFY, http[0], null, listed
                    ? "the container did not stay up: " + seen[0].status()
                    : "the container is not running after start");
        }
        return new Outcome(null, http[0], seen[0], null);
    }

    /**
     * Starts an image the host already holds: the RUN and VERIFY steps of
     * a deploy whose stamp is an earlier build's. Nothing is uploaded and
     * nothing is built; the newer image stays on the host.
     */
    public Outcome rollback(DeployPlan plan, SshTarget target, Listener listener) {
        return runAndVerify(plan, target, listener);
    }

    private boolean step(Step step, String command, SshTarget target, Listener listener, Duration timeout) {
        return step(step, command, target, l -> listener.line(step, l), listener, timeout);
    }

    private boolean step(Step step, String command, SshTarget target,
            java.util.function.Consumer<String> onLine, Listener listener, Duration timeout) {
        listener.stepStarted(step);
        RemoteRunner.Result r = runner.run(target, command, null, onLine, timeout);
        listener.stepFinished(step, r.ok());
        return r.ok();
    }

    private static String lastLine(String tail) {
        String[] lines = tail.strip().split("\\R");
        return lines[lines.length - 1].strip();
    }

    /**
     * The status in a verify line ({@code http <port> <code>}): three
     * digits of a real HTTP status, or {@code "none"}. curl prints
     * {@code 000} when nothing answered, and the 3.11.0 line appended its
     * own "none" to that — a real host whose app was not up yet read
     * "HTTP 000none" on the feed (walked; the simulation always said 200).
     */
    static String httpCode(String line) {
        String[] parts = line.trim().split("\\s+");
        String code = parts.length >= 3 ? parts[2] : "";
        return code.matches("[1-5][0-9][0-9]") ? code : "none";
    }
}

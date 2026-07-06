package org.nmox.studio.rack.docker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.nmox.studio.core.process.ToolLocator;

/**
 * The engine room: an asynchronous wrapper over the real docker CLI.
 * Going through the CLI (rather than the socket API) means contexts,
 * Docker Desktop, colima, and rootless setups all behave exactly as
 * they do in the developer's terminal - and ToolLocator finds the
 * binary even when the IDE was launched from Finder.
 *
 * All list calls use line-delimited {@code --format '{{json .}}'}
 * output; the parsers are pure static functions, tested on canned
 * output without a daemon.
 */
public final class DockerClient {

    private static final DockerClient INSTANCE = new DockerClient();

    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "nmox-docker");
        t.setDaemon(true);
        return t;
    });

    /** The CLI to invoke — "docker" in production, overridable for tests. */
    private final String executable;

    private DockerClient() {
        this("docker");
    }

    /** Test seam: run some other binary through the exact production path. */
    DockerClient(String executable) {
        this.executable = executable;
    }

    public static DockerClient getDefault() {
        return INSTANCE;
    }

    // ---- raw execution ----

    /** One finished CLI call. */
    public record Result(int exit, String stdout, String stderr) {

        public boolean ok() {
            return exit == 0;
        }
    }

    /** Runs docker with the given args; never throws, never prompts. */
    public Result run(long timeoutSeconds, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable);
        Collections.addAll(cmd, args);
        try {
            ProcessBuilder pb = org.nmox.studio.core.process.ProcessSupport.builder(cmd);
            Process p = pb.start();
            // BOTH streams drain on their own threads while waitFor runs first
            // on this pool thread — so the timeout clock ticks even when the
            // CLI sits silent with its pipes open (the wedged-daemon hang).
            // On a timeout, destroyForcibly() closes both pipes and unblocks
            // both drains, so the timeout guarantee is real; the bounded joins
            // after just let the last buffered bytes land.
            StringBuilder outBuf = new StringBuilder();
            StringBuilder errBuf = new StringBuilder();
            Thread outDrain = drainOnThread(p.getInputStream(), outBuf, "docker-stdout");
            Thread errDrain = drainOnThread(p.getErrorStream(), errBuf, "docker-stderr");
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                outDrain.join(2000);
                errDrain.join(2000);
                return new Result(-1, snapshot(outBuf),
                        "docker timed out after " + timeoutSeconds + "s");
            }
            outDrain.join(2000);
            errDrain.join(2000);
            return new Result(p.exitValue(), snapshot(outBuf), snapshot(errBuf));
        } catch (IOException ex) {
            return new Result(-1, "", "docker not found - install Docker Desktop or add docker to PATH");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(-1, "", "interrupted");
        }
    }

    /** Consumes one stream to EOF on a daemon thread; appends under the buffer's lock. */
    private static Thread drainOnThread(java.io.InputStream in, StringBuilder into, String name) {
        Thread t = new Thread(() -> {
            try {
                String all = readAll(in);
                synchronized (into) {
                    into.append(all);
                }
            } catch (IOException ignore) {
                // pipe closed by the kill path; whatever landed is still useful
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static String snapshot(StringBuilder sb) {
        synchronized (sb) {
            return sb.toString();
        }
    }

    private static String readAll(java.io.InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    private <T> CompletableFuture<T> async(java.util.function.Supplier<T> s) {
        return CompletableFuture.supplyAsync(s, pool);
    }

    // ---- engine ----

    /** Server version, or null when the daemon is unreachable. */
    public CompletableFuture<String> engineVersion() {
        return async(() -> {
            Result r = run(8, "version", "--format", "{{.Server.Version}}");
            String v = r.stdout.trim();
            return r.ok() && !v.isEmpty() ? v : null;
        });
    }

    // ---- model records ----

    public record ContainerInfo(String id, String name, String image, String state,
            String status, String ports, List<Integer> hostPorts) {

        public boolean running() {
            return "running".equalsIgnoreCase(state);
        }
    }

    public record ImageInfo(String repository, String tag, String id, String size,
            String created, boolean dangling) {

        public String ref() {
            return dangling ? id : repository + ":" + tag;
        }
    }

    public record VolumeInfo(String name, String driver) {
    }

    public record NetworkInfo(String id, String name, String driver, String scope) {
    }

    /** One row of `docker system df`: a category and what it would free. */
    public record DfRow(String type, String totalCount, String active,
            String size, String reclaimable) {
    }

    public record StatRow(String id, String name, String cpu, String mem) {
    }

    // ---- listings ----

    public CompletableFuture<List<ContainerInfo>> containers() {
        return async(() -> parseContainers(
                run(15, "ps", "-a", "--no-trunc", "--format", "{{json .}}").stdout));
    }

    public CompletableFuture<List<ImageInfo>> images() {
        return async(() -> parseImages(
                run(15, "images", "--format", "{{json .}}").stdout));
    }

    public CompletableFuture<List<VolumeInfo>> volumes() {
        return async(() -> parseVolumes(
                run(15, "volume", "ls", "--format", "{{json .}}").stdout));
    }

    public CompletableFuture<List<NetworkInfo>> networks() {
        return async(() -> parseNetworks(
                run(15, "network", "ls", "--format", "{{json .}}").stdout));
    }

    public CompletableFuture<List<DfRow>> systemDf() {
        return async(() -> parseDf(
                run(30, "system", "df", "--format", "{{json .}}").stdout));
    }

    public CompletableFuture<List<StatRow>> statsSnapshot() {
        return async(() -> parseStats(
                run(20, "stats", "--no-stream", "--format", "{{json .}}").stdout));
    }

    // ---- detail ----

    public CompletableFuture<String> logs(String id, int tail) {
        return async(() -> {
            Result r = run(20, "logs", "--tail", String.valueOf(tail), "--timestamps", id);
            return r.stdout + (r.stderr.isEmpty() ? "" : "\n" + r.stderr);
        });
    }

    public CompletableFuture<String> inspect(String id) {
        return async(() -> run(15, "inspect", id).stdout);
    }

    public CompletableFuture<String> history(String image) {
        return async(() -> run(15, "history", "--no-trunc", "--format",
                "{{.Size}}\t{{.CreatedBy}}", image).stdout);
    }

    // ---- verbs ----

    public CompletableFuture<Result> lifecycle(String verb, String id) {
        return async(() -> switch (verb) {
            case "start", "stop", "restart", "pause", "unpause", "kill" ->
                run(60, verb, id);
            case "rm" -> run(60, "rm", "-f", id);
            default -> new Result(-1, "", "unknown verb " + verb);
        });
    }

    public CompletableFuture<Result> removeImage(String ref, boolean force) {
        return async(() -> force ? run(60, "rmi", "-f", ref) : run(60, "rmi", ref));
    }

    public CompletableFuture<Result> pull(String ref) {
        return async(() -> run(600, "pull", ref));
    }

    public CompletableFuture<Result> tag(String src, String target) {
        return async(() -> run(15, "tag", src, target));
    }

    public CompletableFuture<Result> removeVolume(String name) {
        return async(() -> run(30, "volume", "rm", name));
    }

    public CompletableFuture<Result> removeNetwork(String id) {
        return async(() -> run(30, "network", "rm", id));
    }

    /** kind: container|image|volume|network|builder. all=true widens image prune. */
    public CompletableFuture<Result> prune(String kind, boolean all) {
        return async(() -> switch (kind) {
            case "container" -> run(120, "container", "prune", "-f");
            case "image" -> all ? run(300, "image", "prune", "-a", "-f")
                    : run(300, "image", "prune", "-f");
            case "volume" -> run(120, "volume", "prune", "-f");
            case "network" -> run(120, "network", "prune", "-f");
            case "builder" -> run(300, "builder", "prune", "-f");
            default -> new Result(-1, "", "unknown prune kind " + kind);
        });
    }

    // ---- pure parsers (unit-tested without a daemon) ----

    static List<JSONObject> jsonLines(String out) {
        List<JSONObject> rows = new ArrayList<>();
        for (String line : out.split("\n")) {
            line = line.trim();
            if (line.startsWith("{")) {
                try {
                    rows.add(new JSONObject(line));
                } catch (RuntimeException ignored) {
                    // partial line from a dying daemon; skip it
                }
            }
        }
        return rows;
    }

    /**
     * "0.0.0.0:8080->80/tcp, :::8080->80/tcp" -> [8080]. The published host
     * port is the run of digits immediately before "->"; matching just that
     * is linear-time (the old host-matching alternation could backtrack — ReDoS).
     */
    private static final Pattern HOST_PORT = Pattern.compile(":(\\d+)->");

    static List<Integer> hostPorts(String ports) {
        List<Integer> result = new ArrayList<>();
        Matcher m = HOST_PORT.matcher(ports == null ? "" : ports);
        while (m.find()) {
            Integer p = Integer.valueOf(m.group(1));
            if (!result.contains(p)) {
                result.add(p);
            }
        }
        return result;
    }

    static List<ContainerInfo> parseContainers(String out) {
        List<ContainerInfo> list = new ArrayList<>();
        for (JSONObject o : jsonLines(out)) {
            String ports = o.optString("Ports", "");
            list.add(new ContainerInfo(
                    o.optString("ID", ""),
                    o.optString("Names", ""),
                    o.optString("Image", ""),
                    o.optString("State", ""),
                    o.optString("Status", ""),
                    ports,
                    hostPorts(ports)));
        }
        return list;
    }

    static List<ImageInfo> parseImages(String out) {
        List<ImageInfo> list = new ArrayList<>();
        for (JSONObject o : jsonLines(out)) {
            String repo = o.optString("Repository", "");
            String tag = o.optString("Tag", "");
            list.add(new ImageInfo(repo, tag,
                    o.optString("ID", ""),
                    o.optString("Size", ""),
                    o.optString("CreatedSince", ""),
                    "<none>".equals(repo) || "<none>".equals(tag)));
        }
        return list;
    }

    static List<VolumeInfo> parseVolumes(String out) {
        List<VolumeInfo> list = new ArrayList<>();
        for (JSONObject o : jsonLines(out)) {
            list.add(new VolumeInfo(o.optString("Name", ""), o.optString("Driver", "")));
        }
        return list;
    }

    static List<NetworkInfo> parseNetworks(String out) {
        List<NetworkInfo> list = new ArrayList<>();
        for (JSONObject o : jsonLines(out)) {
            list.add(new NetworkInfo(
                    o.optString("ID", ""),
                    o.optString("Name", ""),
                    o.optString("Driver", ""),
                    o.optString("Scope", "")));
        }
        return list;
    }

    static List<DfRow> parseDf(String out) {
        List<DfRow> list = new ArrayList<>();
        for (JSONObject o : jsonLines(out)) {
            list.add(new DfRow(
                    o.optString("Type", ""),
                    o.optString("TotalCount", ""),
                    o.optString("Active", ""),
                    o.optString("Size", ""),
                    o.optString("Reclaimable", "")));
        }
        return list;
    }

    static List<StatRow> parseStats(String out) {
        List<StatRow> list = new ArrayList<>();
        for (JSONObject o : jsonLines(out)) {
            list.add(new StatRow(
                    o.optString("Container", ""),
                    o.optString("Name", ""),
                    o.optString("CPUPerc", ""),
                    o.optString("MemUsage", "")));
        }
        return list;
    }
}

package org.nmox.studio.infra.ops.remote;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * One ssh round trip that reads everything the HUD shows about a host:
 * load, cores, memory, disk, uptime, whether mosh-server and docker are
 * there, cloud-init's verdict, and the running containers. The script is
 * POSIX sh and degrades per line — a Mac prints no {@code /proc}, a host
 * without docker prints no containers — and the parser turns an absent
 * line into an absent fact, never a zero.
 *
 * <p>Every line the script prints starts with a key, so a stray message
 * from a login shell (a MOTD, a warning) cannot be read as a measurement.
 */
public final class HostProbe {

    /** The whole probe's leash; a host that takes longer is UNREACHABLE for this round. */
    public static final Duration TIMEOUT = Duration.ofSeconds(25);

    /**
     * The most lines one probe may print. The script prints about a
     * dozen plus one per container; a host (or a login shell) that
     * prints more than this is not answering the probe, and its output
     * is dropped past the cap — never collected until the leash fires.
     */
    public static final int MAX_LINES = 2_000;
    /** The most containers one host shows on the HUD; the rest are counted, not drawn. */
    public static final int MAX_CONTAINERS = 200;

    /**
     * A probe's answer: the facts, or why there are none. The problem is
     * the host's (or ssh's) own last words where it had any — "Permission
     * denied (publickey)", "Connection refused", "Host key verification
     * failed" — because a host that never answers must be able to say why.
     */
    public record Reading(HostFacts facts, String problem) {

        public boolean ok() {
            return facts != null;
        }
    }

    /** The script. Keep every line keyed; keep it POSIX. */
    public static final String SCRIPT = String.join("; ",
            "echo nmox-probe 1",
            "echo os $(uname -s)",
            "echo load $( (cut -d' ' -f1 /proc/loadavg 2>/dev/null) || (sysctl -n vm.loadavg 2>/dev/null | tr -d '{}' | cut -d' ' -f2) )",
            "echo cpus $(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null)",
            "free -k 2>/dev/null | awk 'NR==2{print \"mem\",$2,$3}'",
            "df -Pk / 2>/dev/null | awk 'NR==2{print \"disk\",$2,$3}'",
            "echo uptime $(cut -d. -f1 /proc/uptime 2>/dev/null)",
            "command -v mosh-server >/dev/null 2>&1 && echo mosh yes || echo mosh no",
            "command -v docker >/dev/null 2>&1 && echo docker yes || echo docker no",
            "command -v cloud-init >/dev/null 2>&1 && echo cloudinit $(cloud-init status 2>/dev/null | head -1 | sed 's/^status: *//')",
            "docker ps --format '{{json .}}' 2>/dev/null | sed 's/^/container /'",
            "echo nmox-probe end");

    private HostProbe() {
    }

    /** Runs the probe; null when the host did not answer with a complete probe (unreachable, or not a shell). */
    public static HostFacts probe(RemoteRunner runner, SshTarget target) {
        return read(runner, target).facts();
    }

    /** Runs the probe and keeps the reason when it fails. Bounded: at most {@link #MAX_LINES} lines are ever held. */
    public static Reading read(RemoteRunner runner, SshTarget target) {
        List<String> lines = new ArrayList<>();
        int[] dropped = {0};
        String[] last = {null};
        RemoteRunner.Result result = runner.run(target, SCRIPT, null, line -> {
            synchronized (lines) {
                if (lines.size() < MAX_LINES) {
                    lines.add(line);
                } else {
                    dropped[0]++;
                }
                if (!line.isBlank()) {
                    last[0] = line.strip();
                }
            }
        }, TIMEOUT);
        synchronized (lines) {
            if (dropped[0] > 0) {
                return new Reading(null, "the host printed more than " + MAX_LINES + " lines to a probe that prints a dozen");
            }
            if (result.timedOut()) {
                return new Reading(null, "no answer within " + TIMEOUT.toSeconds() + " s");
            }
            HostFacts facts = parse(lines);
            if (facts != null) {
                return new Reading(facts, null);
            }
            // ssh's own complaint is the last thing printed; a host that answered with something else is named as that
            String said = last[0] != null ? last[0] : result.tail() == null || result.tail().isBlank() ? null : result.tail().strip();
            return new Reading(null, clip(said == null ? "ssh exited " + result.exitCode() + " without a word" : said));
        }
    }

    /** One line of a problem, short enough for a feed and an inspector row. */
    static String clip(String s) {
        String one = s.replaceAll("\\s+", " ").strip();
        return one.length() <= 200 ? one : one.substring(0, one.offsetByCodePoints(0, Math.min(one.codePointCount(0, one.length()), 197))) + "…";
    }

    /**
     * Parses the probe's output. Null unless both the opening and closing
     * marker arrived — a half-printed probe is not a measurement.
     */
    public static HostFacts parse(List<String> lines) {
        boolean opened = false;
        boolean closed = false;
        String os = null;
        double load = Double.NaN;
        int cpus = -1;
        long memTotal = -1;
        long memUsed = -1;
        long diskTotal = -1;
        long diskUsed = -1;
        long uptime = -1;
        boolean mosh = false;
        boolean docker = false;
        String cloudInit = null;
        List<HostFacts.ContainerFact> containers = new ArrayList<>();
        int notShown = 0;
        for (String raw : lines) {
            String line = raw.strip();
            int sp = line.indexOf(' ');
            String key = sp < 0 ? line : line.substring(0, sp);
            String rest = sp < 0 ? "" : line.substring(sp + 1).strip();
            switch (key) {
                case "nmox-probe" -> {
                    if ("1".equals(rest)) {
                        opened = true;
                    } else if ("end".equals(rest)) {
                        closed = true;
                    }
                }
                case "os" -> os = rest.isEmpty() ? null : rest;
                case "load" -> load = parseDouble(rest);
                case "cpus" -> cpus = (int) parseLong(rest, -1);
                case "mem" -> {
                    String[] parts = rest.split("\\s+");
                    if (parts.length >= 2) {
                        memTotal = parseLong(parts[0], -1);
                        memUsed = parseLong(parts[1], -1);
                    }
                }
                case "disk" -> {
                    String[] parts = rest.split("\\s+");
                    if (parts.length >= 2) {
                        diskTotal = parseLong(parts[0], -1);
                        diskUsed = parseLong(parts[1], -1);
                    }
                }
                case "uptime" -> uptime = parseLong(rest, -1);
                case "mosh" -> mosh = "yes".equals(rest);
                case "docker" -> docker = "yes".equals(rest);
                case "cloudinit" -> cloudInit = rest.isEmpty() ? null : rest.split("\\s+")[0];
                case "container" -> {
                    HostFacts.ContainerFact c = parseContainer(rest);
                    if (c != null) {
                        if (containers.size() < MAX_CONTAINERS) {
                            containers.add(c);
                        } else {
                            notShown++;
                        }
                    }
                }
                default -> {
                    // a MOTD line, a shell warning: not a fact
                }
            }
        }
        if (!opened || !closed) {
            return null;
        }
        return new HostFacts(os, load, cpus, memTotal, memUsed, diskTotal, diskUsed, uptime,
                mosh, docker, cloudInit, containers, notShown);
    }

    /** One {@code docker ps --format '{{json .}}'} line; null when it is not that. */
    static HostFacts.ContainerFact parseContainer(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String name = o.optString("Names", "");
            if (name.isEmpty()) {
                return null;
            }
            // docker joins labels with commas and a VALUE may hold commas too
            // (compose's depends_on does): a segment without '=' continues the previous value
            Map<String, String> labels = new LinkedHashMap<>();
            String lastKey = null;
            for (String pair : o.optString("Labels", "").split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    lastKey = pair.substring(0, eq).trim();
                    labels.put(lastKey, pair.substring(eq + 1).trim());
                } else if (lastKey != null && !pair.isBlank()) {
                    labels.put(lastKey, labels.get(lastKey) + "," + pair.trim());
                }
            }
            List<String> networks = new ArrayList<>();
            for (String n : o.optString("Networks", "").split(",")) {
                if (!n.isBlank()) {
                    networks.add(n.trim());
                }
            }
            return new HostFacts.ContainerFact(o.optString("ID", ""), name, o.optString("Image", ""),
                    o.optString("Status", ""), o.optString("Ports", ""), labels, networks);
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    private static double parseDouble(String s) {
        try {
            return s.isEmpty() ? Double.NaN : Double.parseDouble(s.split("\\s+")[0].replace(',', '.'));
        } catch (NumberFormatException nan) {
            return Double.NaN;
        }
    }

    private static long parseLong(String s, long absent) {
        try {
            return s.isEmpty() ? absent : Long.parseLong(s.split("\\s+")[0]);
        } catch (NumberFormatException nan) {
            return absent;
        }
    }
}

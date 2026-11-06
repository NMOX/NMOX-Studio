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
        List<String> lines = new ArrayList<>();
        RemoteRunner.Result result = runner.run(target, SCRIPT, null, lines::add, TIMEOUT);
        if (result.timedOut()) {
            return null;
        }
        return parse(lines);
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
                        containers.add(c);
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
                mosh, docker, cloudInit, containers);
    }

    /** One {@code docker ps --format '{{json .}}'} line; null when it is not that. */
    static HostFacts.ContainerFact parseContainer(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String name = o.optString("Names", "");
            if (name.isEmpty()) {
                return null;
            }
            Map<String, String> labels = new LinkedHashMap<>();
            for (String pair : o.optString("Labels", "").split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    labels.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
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

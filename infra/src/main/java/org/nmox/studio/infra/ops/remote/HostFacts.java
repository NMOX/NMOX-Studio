package org.nmox.studio.infra.ops.remote;

import java.util.List;
import java.util.Map;

/**
 * What one probe of a host read, parsed. Numbers are what the host printed;
 * a field the host could not report is NaN, -1 or null — never a default
 * that could be mistaken for a measurement.
 *
 * @param os the kernel name ({@code Linux}, {@code Darwin})
 * @param load1 the one-minute load average
 * @param cpus the core count, -1 unknown
 * @param memTotalKb memory, -1 unknown (a Mac host reports none)
 * @param memUsedKb used memory, -1 unknown
 * @param diskTotalKb the root filesystem, -1 unknown
 * @param diskUsedKb used, -1 unknown
 * @param uptimeSeconds seconds since boot, -1 unknown
 * @param moshServer {@code mosh-server} resolves on the host
 * @param docker {@code docker} resolves on the host
 * @param cloudInit cloud-init's first status word ({@code done}, {@code running}, {@code error}), or null when absent
 * @param containers the running containers, as docker printed them
 */
public record HostFacts(String os, double load1, int cpus, long memTotalKb, long memUsedKb,
        long diskTotalKb, long diskUsedKb, long uptimeSeconds, boolean moshServer, boolean docker,
        String cloudInit, List<ContainerFact> containers) {

    /** One running container, from {@code docker ps --format '{{json .}}'}. */
    public record ContainerFact(String id, String name, String image, String status,
            String ports, Map<String, String> labels, List<String> networks) {

        /** The published host ports, parsed from docker's {@code 0.0.0.0:80->80/tcp, …}. */
        public List<Integer> publishedPorts() {
            List<Integer> out = new java.util.ArrayList<>();
            if (ports == null) {
                return out;
            }
            for (String part : ports.split(",")) {
                String p = part.trim();
                int arrow = p.indexOf("->");
                if (arrow < 0) {
                    continue;
                }
                String left = p.substring(0, arrow);
                int colon = left.lastIndexOf(':');
                String num = colon >= 0 ? left.substring(colon + 1) : left;
                try {
                    int port = Integer.parseInt(num.trim());
                    if (!out.contains(port)) {
                        out.add(port);
                    }
                } catch (NumberFormatException notAPort) {
                    // docker prints ranges too (8000-8010); the HUD shows what it can parse
                }
            }
            return out;
        }

        /** The compose service name when compose started it, else the container name. */
        public String serviceName() {
            String compose = labels.get("com.docker.compose.service");
            return compose != null && !compose.isBlank() ? compose : name;
        }

        /** A compose project name, or null when compose did not start it. */
        public String composeProject() {
            String p = labels.get("com.docker.compose.project");
            return p == null || p.isBlank() ? null : p;
        }

        /**
         * The services this one depends on, as compose recorded them in
         * {@code com.docker.compose.depends_on} ({@code db:service_started:true,cache:…}).
         * DECLARED evidence: someone wrote it in a compose file.
         */
        public List<String> declaredDependencies() {
            List<String> out = new java.util.ArrayList<>();
            String raw = labels.get("com.docker.compose.depends_on");
            if (raw == null || raw.isBlank()) {
                return out;
            }
            for (String entry : raw.split(",")) {
                String name = entry.trim();
                int colon = name.indexOf(':');
                if (colon > 0) {
                    name = name.substring(0, colon);
                }
                if (!name.isEmpty() && !out.contains(name)) {
                    out.add(name);
                }
            }
            return out;
        }

        /** The user-defined networks it is on — {@code bridge}, {@code host} and {@code none} are docker's own. */
        public List<String> userNetworks() {
            List<String> out = new java.util.ArrayList<>();
            for (String n : networks) {
                if (!n.equals("bridge") && !n.equals("host") && !n.equals("none") && !n.isBlank()) {
                    out.add(n);
                }
            }
            return out;
        }
    }
}

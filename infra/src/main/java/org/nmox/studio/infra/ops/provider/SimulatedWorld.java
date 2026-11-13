package org.nmox.studio.infra.ops.provider;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.nmox.studio.infra.ops.remote.DeployPlan;
import org.nmox.studio.infra.ops.remote.RemoteRunner;
import org.nmox.studio.infra.ops.remote.SshTarget;

/**
 * The simulation, in one place: pretend hosts in the documentation range
 * 203.0.113.0/24 (RFC 5737 — no real server has these addresses, so a
 * simulated address can never be mistaken for, or routed to, a live one),
 * a scripted timeline from request to ready, and a {@link RemoteRunner}
 * that answers the probe and the deploy the way a real host would print
 * them. Nothing here touches a network. Every number it invents is
 * labeled simulated by the HUD; the timeline is driven by a clock seam
 * so a test runs it in no time.
 */
public final class SimulatedWorld {

    /** The stages a simulated host passes through, by seconds since creation. */
    static final int ACTIVE_AFTER_S = 5;
    static final int SSH_AFTER_S = 9;
    static final int CLOUD_INIT_DONE_AFTER_S = 16;

    static final class SimHost {
        final String id;
        final String name;
        final String address;
        final long createdAt;
        final Map<String, String> facts = new LinkedHashMap<>();
        final List<SimContainer> containers = new ArrayList<>();
        /** Every image a simulated build made, as {@code nmox/app:stamp} — what a rollback reads back. */
        final List<String> images = new ArrayList<>();
        boolean destroyed;

        SimHost(String id, String name, String address, long createdAt) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.createdAt = createdAt;
        }
    }

    record SimContainer(String name, String image, int hostPort, int containerPort, String app, long since) {
    }

    private final LongSupplier clock;
    private final Map<String, SimHost> hosts = new LinkedHashMap<>();
    private int nextId = 1;

    public SimulatedWorld(LongSupplier clock) {
        this.clock = clock;
    }

    /** True for an address the simulation owns. */
    public static boolean owns(String address) {
        return address != null && address.startsWith("203.0.113.");
    }

    synchronized SimHost create(HostSpec spec) {
        int n = nextId++;
        SimHost h = new SimHost("sim-" + n, spec.name(), "203.0.113." + (10 + (n % 240)), clock.getAsLong());
        h.facts.put("region", spec.region());
        h.facts.put("size", spec.size());
        h.facts.put("image", spec.image());
        h.facts.put("made here", "yes");
        hosts.put(h.id, h);
        return h;
    }

    synchronized List<SimHost> list() {
        List<SimHost> out = new ArrayList<>();
        for (SimHost h : hosts.values()) {
            if (!h.destroyed) {
                out.add(h);
            }
        }
        return out;
    }

    synchronized SimHost byId(String id) {
        SimHost h = hosts.get(id);
        return h == null || h.destroyed ? null : h;
    }

    synchronized SimHost byAddress(String address) {
        for (SimHost h : hosts.values()) {
            if (!h.destroyed && h.address.equals(address)) {
                return h;
            }
        }
        return null;
    }

    synchronized void destroy(String id) {
        SimHost h = hosts.get(id);
        if (h != null) {
            h.destroyed = true;
        }
    }

    long ageSeconds(SimHost h) {
        return (clock.getAsLong() - h.createdAt) / 1000;
    }

    HostRecord record(SimHost h) {
        long age = ageSeconds(h);
        boolean active = age >= ACTIVE_AFTER_S;
        return new HostRecord(h.id, h.name, active ? h.address : null,
                active ? HostRecord.Phase.RUNNING : HostRecord.Phase.COMING_UP, active ? "active" : "new", h.facts);
    }

    /** The simulation's ssh: refuses before the host "boots", then answers the probe and the deploy from the script. */
    public RemoteRunner runner() {
        return new RemoteRunner() {
            @Override
            public Result run(SshTarget target, String remoteCommand, InputStream stdin,
                    Consumer<String> onLine, Duration timeout) {
                SimHost h = byAddress(target.address());
                if (h == null) {
                    onLine.accept("ssh: connect to host " + target.address() + ": No route to host");
                    return new Result(255, "no route to host", false);
                }
                long age = ageSeconds(h);
                if (age < SSH_AFTER_S) {
                    onLine.accept("ssh: connect to host " + target.address() + " port 22: Connection refused");
                    return new Result(255, "connection refused", false);
                }
                if (remoteCommand.startsWith("echo nmox-probe 1")) {
                    for (String line : probeOutput(h, age)) {
                        onLine.accept(line);
                    }
                    return new Result(0, "", false);
                }
                return deployStep(h, remoteCommand, stdin, onLine);
            }
        };
    }

    List<String> probeOutput(SimHost h, long age) {
        List<String> out = new ArrayList<>();
        out.add("nmox-probe 1");
        out.add("os Linux");
        // a slow wave so the gauges move, deterministic per host and second
        double base = 0.18 + 0.12 * h.containers.size();
        double load = base + 0.15 * Math.sin((age + h.id.hashCode()) / 7.0) + 0.05 * Math.sin(age / 2.3);
        out.add(String.format(java.util.Locale.ROOT, "load %.2f", Math.max(0.01, load)));
        out.add("cpus 1");
        long memTotal = 1_000_000;
        long memUsed = 180_000 + h.containers.size() * 90_000 + (age % 50) * 1_000;
        out.add("mem " + memTotal + " " + memUsed);
        out.add("disk 25000000 " + (3_200_000 + h.containers.size() * 400_000));
        out.add("uptime " + age);
        boolean configured = age >= CLOUD_INIT_DONE_AFTER_S;
        out.add("mosh " + (configured ? "yes" : "no"));
        out.add("docker " + (configured ? "yes" : "no"));
        out.add("cloudinit " + (configured ? "done" : "running"));
        List<SimContainer> running;
        synchronized (this) {
            // a deploy step replaces the container on another thread while a probe reads the list
            running = new ArrayList<>(h.containers);
        }
        for (SimContainer c : running) {
            out.add("container {\"ID\":\"" + Integer.toHexString(c.name.hashCode()) + "\",\"Names\":\"" + c.name
                    + "\",\"Image\":\"" + c.image + "\",\"Status\":\"Up " + ((clock.getAsLong() - c.since) / 1000)
                    + " seconds\",\"Ports\":\"0.0.0.0:" + c.hostPort + "->" + c.containerPort
                    + "/tcp\",\"Labels\":\"" + DeployPlan.LABEL + "=" + c.app + "\",\"Networks\":\"bridge\"}");
        }
        out.add("nmox-probe end");
        return out;
    }

    private RemoteRunner.Result deployStep(SimHost h, String command, InputStream stdin, Consumer<String> onLine) {
        if (ageSeconds(h) < CLOUD_INIT_DONE_AFTER_S && command.startsWith("docker ")) {
            onLine.accept("bash: docker: command not found");
            return new RemoteRunner.Result(127, "docker: command not found", false);
        }
        if (command.contains("tar xzf -")) {
            long bytes = 0;
            if (stdin != null) {
                try (stdin) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = stdin.read(buf)) > 0) {
                        bytes += n;
                    }
                } catch (java.io.IOException ignored) {
                    // the writer closed; what arrived is counted
                }
            }
            onLine.accept("received " + bytes + " bytes (simulated)");
            return new RemoteRunner.Result(0, "", false);
        }
        if (command.startsWith("set -e; cd ")) {
            return new RemoteRunner.Result(0, "", false);
        }
        if (command.startsWith("docker build")) {
            String image = command.split("\\s+")[3];
            for (String line : List.of("#1 [internal] load build definition from Dockerfile",
                    "#2 [internal] load .dockerignore", "#3 [1/4] FROM docker.io/library/node:26-alpine",
                    "#4 [2/4] WORKDIR /app", "#5 [3/4] COPY . .", "#6 [4/4] RUN npm ci --omit=dev (simulated)",
                    "#7 exporting to image", "#7 naming to " + image + " done")) {
                onLine.accept(line);
                pause(350);
            }
            synchronized (this) {
                if (!h.images.contains(image)) {
                    h.images.add(image);
                }
            }
            return new RemoteRunner.Result(0, "", false);
        }
        if (command.startsWith("docker images nmox/")) {
            String repo = command.split("\\s+")[2];
            synchronized (this) {
                for (String image : h.images) {
                    if (image.startsWith(repo + ":")) {
                        onLine.accept(image.substring(repo.length() + 1));
                    }
                }
            }
            onLine.accept("latest");
            return new RemoteRunner.Result(0, "", false);
        }
        if (command.startsWith("docker rm -f")) {
            String[] parts = command.split("\\s+");
            String name = parts[3];
            int p = command.indexOf(" -p ");
            String[] ports = command.substring(p + 4).split("\\s+")[0].split(":");
            String image = parts[parts.length - 1];
            String app = name.startsWith("nmox-") ? name.substring(5) : name;
            synchronized (this) {
                h.containers.removeIf(c -> c.name.equals(name));
                h.containers.add(new SimContainer(name, image, Integer.parseInt(ports[0]), Integer.parseInt(ports[1]),
                        app, clock.getAsLong()));
            }
            onLine.accept(Integer.toHexString(name.hashCode()) + "a9c0ffee");
            return new RemoteRunner.Result(0, "", false);
        }
        if (command.startsWith("sleep 2; docker ps")) {
            pause(600);
            synchronized (this) {
                for (SimContainer c : h.containers) {
                    if (command.contains("name=^" + c.name + "$")) {
                        onLine.accept("{\"ID\":\"" + Integer.toHexString(c.name.hashCode()) + "\",\"Names\":\"" + c.name
                                + "\",\"Image\":\"" + c.image + "\",\"Status\":\"Up 2 seconds\",\"Ports\":\"0.0.0.0:"
                                + c.hostPort + "->" + c.containerPort + "/tcp\",\"Labels\":\"" + DeployPlan.LABEL + "="
                                + c.app + "\",\"Networks\":\"bridge\"}");
                        onLine.accept("http " + c.hostPort + " 200");
                    }
                }
            }
            return new RemoteRunner.Result(0, "", false);
        }
        onLine.accept("simulation: no script for: " + command);
        return new RemoteRunner.Result(1, "unscripted", false);
    }

    /** The simulation's pacing; a test clock makes it zero. */
    void pause(long millis) {
        if (pacing) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private volatile boolean pacing = true;

    /** Test seam: no sleeps. */
    public SimulatedWorld unpaced() {
        pacing = false;
        return this;
    }
}

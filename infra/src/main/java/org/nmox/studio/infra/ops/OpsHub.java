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
    "OpsHub_simulationName=Simulation",
    "OpsHub_sourceAddedByYou=added by you",
    "# {0} - the provider's name",
    "OpsHub_sourceListedBy=listed by {0}",
    "# {0} - the host's label",
    "OpsHub_sourceDockerPs=docker ps on {0}",
    "# {0} - the network's name",
    "OpsHub_sourceSharedNetwork=shared network {0}",
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
    "OpsHub_listsAt={1} is listed by {0} at {2}",
    "# {0} - the provider's name",
    "# {1} - the host's label",
    "OpsHub_lists={1} is listed by {0}",
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
    "OpsHub_watchGaveUp={0} is not ready after 15 minutes. The Hub no longer waits for it; from here on it is checked like every other host",
    "# {0} - the host's label",
    "# {1} - the provider's name",
    "OpsHub_goneWhileWatched={1} no longer has {0}, which was still coming up",
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
    "# {1} - what ssh said",
    "OpsHub_stoppedAnswering={0} stopped answering ssh: {1}",
    "# {0} - the host's label",
    "# {1} - what ssh said",
    "OpsHub_doesNotAnswer={0} does not answer ssh: {1}",
    "# {0} - seconds",
    "OpsHub_probeNoAnswer=no answer within {0} s",
    "# {0} - ssh's exit code",
    "OpsHub_probeSilentExit=ssh ended with exit code {0} and said nothing",
    "# {0} - a number of lines",
    "OpsHub_probeTooManyLines=the host printed more than {0} lines where a few dozen were expected",
    "# {0} - the provider's name",
    "# {1} - the provider's word for the server's state (off, archive)",
    "OpsHub_providerSaysNotRunning={0} reports this server as: {1}",
    "# {0} - the host's label",
    "# {1} - cloud-init's verdict (error, degraded)",
    "OpsHub_readyCloudInitTrouble={0} answers ssh, but cloud-init reports: {1} — docker and mosh-server may be missing",
    "# {0} - the host's label",
    "OpsHub_answersSshConfiguring=ssh answers on {0} — cloud-init still running",
    "# {0} - the host's label",
    "OpsHub_answersSsh=ssh answers on {0}",
    "# {0} - the host's label",
    "OpsHub_ready={0} is ready (cloud-init done)",
    "# {0} - the app slug",
    "OpsHub_deployedByFact=NMOX Studio ({0})",
    "# {0} - the host's label",
    "# {1} - the service name",
    "# {2} - the image",
    "OpsHub_runs={1} is running on {0} ({2})",
    "# {0} - the host's label",
    "# {1} - the service name",
    "OpsHub_noLongerRuns={1} is no longer running on {0}",
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
    "OpsHub_saidNothing=the step printed nothing",
    "# {0} - docker's own status for the container",
    "OpsHub_didNotStayUp=the container did not stay up: {0}",
    "OpsHub_notRunningAfterStart=the container is not running after start",
    "OpsHub_stepBundle=bundle",
    "OpsHub_stepUpload=upload",
    "OpsHub_stepSwap=swap",
    "OpsHub_stepBuild=build",
    "OpsHub_stepRun=run",
    "OpsHub_stepVerify=verify",
    "# {0} - the service's label",
    "OpsHub_refuseNotOurs={0} was not deployed from NMOX Studio, so the Hub cannot tell which image ran before it",
    "# {0} - the service",
    "# {1} - the image it runs",
    "OpsHub_refuseForeignImage={0} runs {1}, which is not an image NMOX Studio tagged, so the Hub cannot tell which one ran before it",
    "# {0} - the service's label",
    "OpsHub_refuseNoPorts={0} has no port mapping the Hub can read (none, or a range) — a rollback would not know which ports to give the container",
    "# {0} - the app slug",
    "# {1} - the stamp of the image running now",
    "OpsHub_refuseNoEarlier=the host holds no image of {0} older than the one running ({1})",
    "# {0} - the app slug",
    "# {1} - the host's label",
    "# {2} - what ssh or docker said",
    "OpsHub_refuseImagesUnread=could not read the images of {0} on {1}: {2}",
    "# {0} - the reason",
    "OpsHub_rollbackRefused=Rollback refused: {0}",
    "# {0} - the app slug",
    "# {1} - the host's label",
    "OpsHub_readingImages=Reading the images of {0} on {1}…",
    "# {0} - the app slug",
    "# {1} - the host's label",
    "# {2} - the stamp running now",
    "# {3} - the stamp going back to",
    "OpsHub_rollingBack=Rolling {0} on {1} back from image {2} to image {3}",
    "# {0} - the app slug",
    "# {1} - the host's label",
    "# {2} - the stamp now running",
    "# {3} - the HTTP answer",
    "OpsHub_rolledBack={0} on {1} runs image {2} again ({3})",
    "# {0} - the step",
    "# {1} - the reason",
    "OpsHub_rollbackStopped=Rollback stopped at {0}: {1}"
})
public final class OpsHub {

    private static final Logger LOG = Logger.getLogger(OpsHub.class.getName());
    private static final RequestProcessor CLOUD_RP = new RequestProcessor("Ops Hub cloud", 2, true);
    private static final RequestProcessor SSH_RP = new RequestProcessor("Ops Hub ssh", 4, true);

    /** A worker lane: the product's are request processors; a test's is a queue it drains by hand. */
    public interface Lane {
        /** Runs {@code r} after {@code delayMillis}; the handle cancels it if it has not started. */
        Runnable post(Runnable r, long delayMillis);
    }

    private void onCloud(Runnable r) {
        cloud.post(r, 0);
    }

    private void onSsh(Runnable r) {
        ssh.post(r, 0);
    }

    static Lane lane(RequestProcessor rp) {
        return (r, delay) -> {
            RequestProcessor.Task task = rp.post(r, (int) delay);
            return task::cancel;
        };
    }

    /** How often reachable hosts are probed while the Hub is showing. */
    public static final int POLL_SECONDS = 15;
    /** Every this-many poll ticks the providers are listed again (once a minute). */
    static final int REFRESH_EVERY_TICKS = 4;
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
                    new ProcessRemoteRunner(knownHostsFile()), NbPreferences.forModule(OpsHub.class).node("ops"),
                    lane(CLOUD_RP), lane(SSH_RP));
        }
        return instance;
    }

    /** The Hub if this session has made one, else null — for a reader that must never be the one to make it. */
    public static synchronized OpsHub peek() {
        return instance;
    }

    /**
     * One row of what Quick Search may find: a host or a service, as
     * plain values. The topology is the event thread's; the search runs
     * on its own, so it reads this copy, rebuilt on the event thread
     * whenever the topology changes.
     *
     * @param hostLabel a service's host; null on a host
     * @param status a host's status; null on a service
     */
    public record Findable(String id, OpsKind kind, String label, String address, String hostLabel,
            HostStatus status, boolean simulated) {
    }

    private volatile List<Findable> findable = List.of();

    /** Hosts and services as they stood at the last change — safe from any thread. */
    public List<Findable> findable() {
        return findable;
    }

    private void reindex() {
        List<Findable> out = new ArrayList<>();
        for (OpsNode n : topology.nodes()) {
            if (n.kind == OpsKind.HOST) {
                out.add(new Findable(n.id, n.kind, n.label, n.address, null, n.status, n.simulated));
            } else if (n.kind == OpsKind.SERVICE) {
                OpsNode host = n.hostId == null ? null : topology.node(n.hostId);
                out.add(new Findable(n.id, n.kind, n.label, host == null ? null : host.address,
                        host == null ? null : host.label, null, n.simulated));
            }
        }
        findable = List.copyOf(out);
    }

    private final java.util.function.LongSupplier clock;
    private final Consumer<Runnable> edt;
    private final RemoteRunner sshRunner;
    private final Preferences prefs;
    private final Lane cloud;
    private final Lane ssh;
    private final Topology topology = new Topology();
    private final List<Listener> listeners = new ArrayList<>();
    private final Deque<OpsEvent> feed = new ArrayDeque<>();
    private final Map<String, OpsProvider> providers = new LinkedHashMap<>();
    /**
     * Probes in flight, by NODE (not by id: a host forgotten and added again is another node, and
     * the first one's late answer is not its), each with whoever is waiting for the answer. EDT.
     */
    private final Map<OpsNode, List<Consumer<HostFacts>>> probing = new java.util.IdentityHashMap<>();
    /** Hosts a provisioning watch is following: while one is, "ssh refused" is expected and said nowhere. EDT. */
    private final Set<OpsNode> watched = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** Watches whose provider read is failing, so the failure is announced once and not every five seconds. EDT. */
    private final Set<OpsNode> watchErrored = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private int pollTicks;
    private final Map<String, DeployRun> deploys = new LinkedHashMap<>();
    private final AtomicBoolean polling = new AtomicBoolean();
    private Runnable pollCancel;
    private SimulatedOps simulation;
    private Boolean moshOnPath;

    OpsHub(java.util.function.LongSupplier clock, Consumer<Runnable> edt, RemoteRunner sshRunner, Preferences prefs,
            Lane cloud, Lane ssh) {
        this.clock = clock;
        this.edt = edt;
        this.sshRunner = sshRunner;
        this.prefs = prefs;
        this.cloud = cloud;
        this.ssh = ssh;
        for (OpsProvider p : Lookup.getDefault().lookupAll(OpsProvider.class)) {
            providers.put(p.id(), p);
        }
        topology.addListener(new Topology.Listener() {
            @Override
            public void structureChanged() {
                reindex();
            }

            @Override
            public void nodeChanged(OpsNode node) {
                reindex();
            }
        });
        loadManualHosts();
        reindex();
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
            simulation = new SimulatedOps(new SimulatedWorld(clock), Bundle.OpsHub_simulationName());
            providers.put(simulation.id(), simulation);
            event(OpsEvent.Kind.INFO, null, Bundle.OpsHub_simulationOn());
        } else {
            providers.remove(simulation.id());
            simulation = null;
            for (OpsNode n : topology.nodes()) {
                if (n.simulated) {
                    topology.remove(n.id);
                    // its run goes with it: the next simulation numbers its hosts from one again,
                    // and a new sim-1 must not open on the last world's deploy
                    deploys.remove(n.id);
                    watched.remove(n);
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
            topology.connect(new OpsEdge(Topology.WORKSTATION_ID, id, "ssh", Evidence.DECLARED, Bundle.OpsHub_sourceAddedByYou()));
            saveManualHosts();
            event(OpsEvent.Kind.INFO, id, Bundle.OpsHub_addedHost(node.label, target.spec()));
        }
        probe(node);
        return node;
    }

    /** Forgets a manual host (the server is untouched). */
    public void forgetManualHost(OpsNode node) {
        if ("manual".equals(node.provider) && topology.remove(node.id)) {
            // its run goes with it: a manual host's id is its address, so the same server added again
            // under another name showed the forgotten one's last rollback as its own (walked)
            deploys.remove(node.id);
            watchErrored.remove(node);
            saveManualHosts();
            event(OpsEvent.Kind.INFO, node.id, Bundle.OpsHub_forgotHost(node.label));
        }
    }

    private void loadManualHosts() {
        try {
            String[] keys = prefs.node("hosts").keys();
            java.util.Arrays.sort(keys); // the store keeps no order; the HUD should
            for (String key : keys) {
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
                    topology.connect(new OpsEdge(Topology.WORKSTATION_ID, key, "ssh", Evidence.DECLARED, Bundle.OpsHub_sourceAddedByYou()));
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
            // the puts are memory; the flush is the disk, and this runs on the EDT
            onCloud(() -> {
                try {
                    hosts.flush();
                } catch (BackingStoreException | IllegalStateException cannot) {
                    LOG.log(Level.WARNING, "Could not save the Ops Hub's hosts", cannot);
                }
            });
        } catch (BackingStoreException | IllegalStateException cannot) {
            LOG.log(Level.WARNING, "Could not save the Ops Hub's hosts", cannot);
        }
    }

    // ---- providers: list, create, destroy ----

    /** Re-reads every configured provider's hosts, off the EDT; applies on the EDT. */
    public void refresh() {
        for (OpsProvider p : providers()) {
            onCloud(() -> {
                // whether a provider is configured is a keychain read the first time: asked on the lane
                if (!p.configured()) {
                    return;
                }
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
                deploys.remove(n.id);
                // destroyed somewhere else: its address will be handed to another server, whose key
                // must not be refused as "changed" because this one's is still on file
                forgetHostKey(n);
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
            if (node.status == HostStatus.DESTROYING) {
                // the destroy owns its story
            } else if ("new".equals(r.providerStatus()) || node.status == HostStatus.REQUESTED) {
                node.status = HostStatus.PROVISIONING;
            } else {
                // off, archived: it exists and is not coming up — say what the provider says, sweep nothing
                node.status = HostStatus.UNREACHABLE;
                node.lastProblem = Bundle.OpsHub_providerSaysNotRunning(p.displayName(), String.valueOf(r.providerStatus()));
            }
        } else if (!node.status.reachable() && node.status != HostStatus.UNREACHABLE
                && node.status != HostStatus.DESTROYING) {
            node.status = HostStatus.BOOTING;
        }
        if (fresh) {
            topology.put(node);
            topology.connect(new OpsEdge(Topology.WORKSTATION_ID, id, "ssh", Evidence.OBSERVED,
                    Bundle.OpsHub_sourceListedBy(p.displayName())));
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
        onCloud(() -> {
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
                watched.add(node);
                watch(p, node, clock.getAsLong());
            });
        });
    }

    /** One tick of the provisioning watch; reschedules itself until READY, GONE or the limit. Starts on the EDT. */
    private void watch(OpsProvider p, OpsNode node, long since) {
        if (topology.node(node.id) != node) {
            endWatch(node); // destroyed or replaced meanwhile
            return;
        }
        if (clock.getAsLong() - since > WATCH_LIMIT_MILLIS) {
            endWatch(node);
            // it is no longer "becoming": the sweep stops, and the ordinary poll takes over from here
            if (node.status.inProgress()) {
                node.status = HostStatus.UNREACHABLE;
                topology.touched(node);
            }
            event(OpsEvent.Kind.FAIL, node.id, Bundle.OpsHub_watchGaveUp(node.label));
            return;
        }
        onCloud(() -> {
            HostRecord r;
            String failure = null;
            try {
                r = p.readHost(node.providerId);
            } catch (IOException | RuntimeException ex) {
                failure = String.valueOf(ex.getMessage());
                r = null;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            HostRecord read = r;
            String failed = failure;
            edt.accept(() -> {
                if (topology.node(node.id) != node) {
                    endWatch(node); // destroyed or replaced meanwhile
                    return;
                }
                if (failed != null) {
                    // the provider could not be read: say so ONCE, keep trying quietly
                    if (watchErrored.add(node)) {
                        event(OpsEvent.Kind.FAIL, node.id, Bundle.OpsHub_watchFailed(failed));
                    }
                    scheduleWatch(p, node, since, 5);
                    return;
                }
                watchErrored.remove(node);
                if (read == null) {
                    // the provider answered, and its answer is that there is no such server
                    endWatch(node);
                    node.status = HostStatus.GONE;
                    topology.remove(node.id);
                    deploys.remove(node.id);
                    forgetHostKey(node);
                    event(OpsEvent.Kind.FAIL, node.id, Bundle.OpsHub_goneWhileWatched(node.label, p.displayName()));
                    return;
                }
                HostStatus before = node.status;
                applyRecord(p, read);
                if (node.status == HostStatus.PROVISIONING && before == HostStatus.REQUESTED) {
                    event(OpsEvent.Kind.STEP, node.id, Bundle.OpsHub_provisioning(node.label));
                }
                if (read.active() && read.address() != null) {
                    if (!"ssh".equals(node.facts.get("watch"))) {
                        node.facts.put("watch", "ssh");
                        event(OpsEvent.Kind.STEP, node.id, Bundle.OpsHub_activeWaitingForSsh(node.label, read.address()));
                    }
                    probeThen(node, facts -> {
                        if (facts == null || node.status != HostStatus.READY) {
                            scheduleWatch(p, node, since, 6);
                        } else {
                            endWatch(node);
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
        cloud.post(() -> edt.accept(() -> watch(p, node, since)), seconds * 1000L);
    }

    private void endWatch(OpsNode node) {
        watched.remove(node);
        watchErrored.remove(node);
        node.facts.remove("watch");
    }

    /** Destroys a provider host; the node stays as DESTROYING until the provider stops listing it. */
    public void destroyHost(OpsNode node) {
        OpsProvider p = providers.get(node.provider);
        if (p == null || node.providerId == null) {
            return;
        }
        HostStatus was = node.status;
        node.status = HostStatus.DESTROYING;
        topology.touched(node);
        event(OpsEvent.Kind.STEP, node.id, Bundle.OpsHub_destroying(node.label));
        onCloud(() -> {
            try {
                p.destroyHost(node.providerId);
                edt.accept(() -> {
                    endWatch(node);
                    topology.remove(node.id);
                    deploys.remove(node.id);
                    forgetHostKey(node);
                    event(OpsEvent.Kind.OK, node.id, Bundle.OpsHub_destroyed(node.label));
                });
            } catch (IOException | RuntimeException ex) {
                edt.accept(() -> {
                    // it is what it was before the attempt — never READY by default: an unreachable
                    // server whose destroy failed is still unreachable
                    node.status = was;
                    node.lastProblem = ex.getMessage();
                    topology.touched(node);
                    event(OpsEvent.Kind.FAIL, node.id, Bundle.OpsHub_destroyFailed(ex.getMessage()));
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * A destroyed host's address will be reused; its key must not refuse
     * the next tenant. ssh hashes known_hosts entries by default (walked
     * 3.11.0: the Hub's file held {@code |1|…} lines), so the only honest
     * way to drop one is ssh's own {@code ssh-keygen -R}, a fixed argv on
     * the Hub's own file.
     */
    private void forgetHostKey(OpsNode node) {
        String file = knownHosts();
        if (file == null || node.address == null || !Files.exists(Path.of(file))) {
            return;
        }
        String host = knownHostsName(node.address, node.sshPort);
        onSsh(() -> {
            try {
                org.nmox.studio.core.process.ProcessSupport.runBounded(
                        List.of("ssh-keygen", "-R", host, "-f", file), null, java.time.Duration.ofSeconds(10));
            } catch (IOException | RuntimeException cannot) {
                LOG.log(Level.FINE, "could not prune known_hosts", cannot);
            }
        });
    }

    /** The name ssh files a host's key under: the bare address on port 22, {@code [address]:port} otherwise. */
    static String knownHostsName(String address, int port) {
        String bare = address.startsWith("[") && address.endsWith("]") ? address.substring(1, address.length() - 1) : address;
        return port == 22 ? bare : "[" + bare + "]:" + port;
    }

    // ---- probing ----

    /** A simulated node's "ssh" once its world is gone: it says so, and never becomes a real ssh to a made-up address. */
    private static final RemoteRunner NO_SIMULATION = (target, command, stdin, onLine, timeout) -> {
        onLine.accept("the simulation is off");
        return new RemoteRunner.Result(255, "the simulation is off", false);
    };

    private RemoteRunner runnerFor(OpsNode node) {
        if (node.simulated) {
            return simulation != null ? simulation.world().runner() : NO_SIMULATION;
        }
        return sshRunner;
    }

    /** Probes a host now, off the EDT; applies on the EDT. */
    public void probe(OpsNode node) {
        probeThen(node, null);
    }

    /**
     * Probes a node and hands the answer to {@code then} (null for
     * silence). One probe per node at a time: a second ask while one is
     * in flight does not start another — it WAITS for that one's answer,
     * so nobody who asked is ever left without one (the provisioning
     * watch reschedules itself from this callback, and dropping it on a
     * collision with the poll ended the watch without a word).
     */
    private void probeThen(OpsNode node, Consumer<HostFacts> then) {
        SshTarget target = targetOf(node);
        if (target == null) {
            if (then != null) {
                then.accept(null);
            }
            return;
        }
        List<Consumer<HostFacts>> waiting = probing.get(node);
        if (waiting != null) {
            if (then != null) {
                waiting.add(then);
            }
            return;
        }
        waiting = new ArrayList<>();
        if (then != null) {
            waiting.add(then);
        }
        probing.put(node, waiting);
        RemoteRunner runner = runnerFor(node);
        onSsh(() -> {
            HostProbe.Reading reading;
            try {
                reading = HostProbe.read(runner, target);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "probe failed for " + node.id, ex);
                reading = HostProbe.Reading.said(String.valueOf(ex.getMessage()));
            }
            HostProbe.Reading read = reading;
            edt.accept(() -> {
                List<Consumer<HostFacts>> asked = probing.remove(node);
                // by identity: an answer for a node that was forgotten (or replaced by one with the same id) is nobody's
                if (topology.node(node.id) == node) {
                    applyReading(node, read);
                }
                if (asked != null) {
                    for (Consumer<HostFacts> c : asked) {
                        c.accept(read.facts());
                    }
                }
            });
        });
    }

    /** A probe's answer, or its reason for having none, onto the node. EDT. */
    void applyReading(OpsNode node, HostProbe.Reading reading) {
        if (reading.ok()) {
            applyProbe(node, reading.facts());
            return;
        }
        HostStatus before = node.status;
        String problem = problemText(reading);
        boolean changed = !java.util.Objects.equals(node.lastProblem, problem);
        node.lastProblem = problem;
        if (before.reachable()) {
            node.status = HostStatus.UNREACHABLE;
            event(OpsEvent.Kind.FAIL, node.id, Bundle.OpsHub_stoppedAnswering(node.label, problem));
        } else if (before == HostStatus.BOOTING && !watched.contains(node)) {
            // nobody is bringing this one up: a host added by hand with a typo, or one whose key the
            // account does not hold. "Booting" forever would be a lie — it does not answer, and here is why
            node.status = HostStatus.UNREACHABLE;
            event(OpsEvent.Kind.FAIL, node.id, Bundle.OpsHub_doesNotAnswer(node.label, problem));
        }
        if (changed || before != node.status) {
            topology.touched(node);
        }
    }

    /** A failed probe's reason in the reader's language: somebody's own words as they were said, ours from the bundle. */
    static String problemText(HostProbe.Reading reading) {
        return switch (reading.trouble()) {
            case NONE -> null;
            case SAID -> reading.said();
            case NO_ANSWER -> Bundle.OpsHub_probeNoAnswer(Long.toString(reading.number()));
            case SILENT_EXIT -> Bundle.OpsHub_probeSilentExit(Long.toString(reading.number()));
            case TOO_MANY_LINES -> Bundle.OpsHub_probeTooManyLines(Long.toString(reading.number()));
        };
    }

    /** What a probe's answer does to the node and its services. EDT. */
    void applyProbe(OpsNode node, HostFacts facts) {
        HostStatus before = node.status;
        if (facts == null) {
            applyReading(node, HostProbe.Reading.trouble(HostProbe.Trouble.NO_ANSWER, HostProbe.TIMEOUT.toSeconds()));
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
        if (facts.containersNotShown() > 0) {
            node.facts.put("containers not drawn", Integer.toString(facts.containersNotShown()));
        } else {
            node.facts.remove("containers not drawn");
        }
        boolean configuring = "running".equals(facts.cloudInit()) || "not".equals(facts.cloudInit());
        // cloud-init's own word decides what "ready" may claim: only "done" (or no cloud-init at all) is done
        boolean trouble = facts.cloudInit() != null && !configuring && !"done".equals(facts.cloudInit())
                && !"disabled".equals(facts.cloudInit());
        node.status = configuring ? HostStatus.CONFIGURING : HostStatus.READY;
        if (trouble) {
            node.lastProblem = Bundle.OpsHub_readyCloudInitTrouble(node.label, facts.cloudInit());
        }
        if (before != node.status) {
            if (trouble) {
                linkEvent(OpsEvent.Kind.FAIL, node.id, Bundle.OpsHub_readyCloudInitTrouble(node.label, facts.cloudInit()));
            } else if (!before.reachable()) {
                linkEvent(OpsEvent.Kind.OK, node.id, configuring ? Bundle.OpsHub_answersSshConfiguring(node.label) : Bundle.OpsHub_answersSsh(node.label));
            } else if (node.status == HostStatus.READY) {
                linkEvent(OpsEvent.Kind.OK, node.id, Bundle.OpsHub_ready(node.label));
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
            s.app = app;
            if (app != null) {
                s.facts.put("deployed by", Bundle.OpsHub_deployedByFact(app));
            } else {
                s.facts.remove("deployed by");
            }
            if (c.composeProject() != null) {
                s.facts.put("compose project", c.composeProject());
            }
            s.lastSeenMillis = clock.getAsLong();
            topology.put(s);
            if (fresh) {
                linkEvent(OpsEvent.Kind.INFO, id, Bundle.OpsHub_runs(host.label, s.label, c.image()));
            }
            seen.add(id);
            byService.put(c.serviceName(), id);
            edges.add(new OpsEdge(host.id, id, "runs", Evidence.OBSERVED, Bundle.OpsHub_sourceDockerPs(host.label)));
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
                        edges.add(new OpsEdge(a, b, "reaches", Evidence.INFERRED, Bundle.OpsHub_sourceSharedNetwork(shared.get(0))));
                    }
                }
            }
        }
        for (OpsNode s : topology.servicesOf(host.id)) {
            if (!seen.contains(s.id)) {
                topology.remove(s.id);
                linkEvent(OpsEvent.Kind.INFO, s.id, Bundle.OpsHub_noLongerRuns(host.label, s.label));
            }
        }
        topology.replaceEdges(e -> e.from().equals(host.id) && e.kind().equals("runs")
                || e.kind().equals("depends") && e.from().startsWith(host.id + "/")
                || e.kind().equals("reaches") && e.from().startsWith(host.id + "/"), edges);
    }

    /** Probes every host with an address, unless the provider still owns its story (requested, provisioning, destroying). */
    public void probeAll() {
        for (OpsNode n : topology.nodes(OpsKind.HOST)) {
            if (n.address != null && n.status != HostStatus.REQUESTED && n.status != HostStatus.PROVISIONING
                    && n.status != HostStatus.DESTROYING) {
                probe(n);
            }
        }
    }

    /** The window is showing: poll while it is. */
    public void startPolling() {
        if (polling.compareAndSet(false, true)) {
            pollCancel = ssh.post(this::pollTick, 500);
        }
    }

    public void stopPolling() {
        if (polling.compareAndSet(true, false) && pollCancel != null) {
            pollCancel.run();
        }
    }

    private void pollTick() {
        if (!polling.get()) {
            return;
        }
        edt.accept(() -> {
            probeAll();
            // once a minute the providers are asked again too: a server made, powered off or destroyed
            // somewhere else shows up without anyone pressing Refresh
            if (++pollTicks % REFRESH_EVERY_TICKS == 0) {
                refresh();
            }
        });
        pollCancel = ssh.post(this::pollTick, POLL_SECONDS * 1000L);
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
        onSsh(() -> {
            Deployer.Outcome outcome = new Deployer(runner).deploy(project.toPath(), plan, target, tracker(run, host));
            edt.accept(() -> {
                run.outcome = outcome;
                run.finished = true;
                if (outcome.ok()) {
                    event(OpsEvent.Kind.OK, host.id, Bundle.OpsHub_deployUp(plan.app(), host.label,
                            Integer.toString(plan.hostPort()), httpWord(outcome)));
                } else {
                    event(OpsEvent.Kind.FAIL, host.id, Bundle.OpsHub_deployStopped(stepName(outcome.failedAt()), reasonOf(outcome)));
                }
                fireState();
                probe(host);
            });
        });
        return run;
    }

    /**
     * Why a run stopped: the host's or the tool's own words; for a verify
     * that found the container not up, docker's own status for it (or that
     * it is not listed at all); and when a step failed and printed
     * nothing, that it did.
     */
    static String reasonOf(Deployer.Outcome outcome) {
        if (outcome.reason() != null && !outcome.reason().isBlank()) {
            return outcome.reason();
        }
        if (outcome.failedAt() == Deployer.Step.VERIFY) {
            return outcome.container() != null ? Bundle.OpsHub_didNotStayUp(outcome.container().status())
                    : Bundle.OpsHub_notRunningAfterStart();
        }
        return Bundle.OpsHub_saidNothing();
    }

    private static String httpWord(Deployer.Outcome outcome) {
        return outcome.httpCode() == null || "none".equals(outcome.httpCode())
                ? Bundle.OpsHub_noHttpAnswerYet() : "HTTP " + outcome.httpCode();
    }

    /** A run's steps and lines, carried to the EDT as they happen: the tracker the inspector reads and the feed's step lines. */
    private Deployer.Listener tracker(DeployRun run, OpsNode host) {
        return new Deployer.Listener() {
            @Override
            public void stepStarted(Deployer.Step step) {
                edt.accept(() -> {
                    run.steps.put(step, DeployRun.StepState.RUNNING);
                    fireState();
                });
            }

            /** Lines printed since the last hand-over; a build prints hundreds a second. */
            private final java.util.concurrent.ConcurrentLinkedQueue<String> pending = new java.util.concurrent.ConcurrentLinkedQueue<>();
            private final AtomicBoolean handOverQueued = new AtomicBoolean();

            @Override
            public void line(Deployer.Step step, String line) {
                // ONE event-thread task per burst, however many lines it carries: a task per line
                // floods the queue the whole IDE paints from, and each one rebuilt the tracker
                pending.add(line);
                if (handOverQueued.compareAndSet(false, true)) {
                    edt.accept(this::handOver);
                }
            }

            private void handOver() {
                handOverQueued.set(false);
                boolean any = false;
                for (String l = pending.poll(); l != null; l = pending.poll()) {
                    run.line(l);
                    any = true;
                }
                if (any) {
                    fireState();
                }
            }

            @Override
            public void stepFinished(Deployer.Step step, boolean ok) {
                edt.accept(() -> {
                    handOver();
                    run.steps.put(step, ok ? DeployRun.StepState.OK : DeployRun.StepState.FAILED);
                    linkEvent(ok ? OpsEvent.Kind.OK : OpsEvent.Kind.FAIL, host.id, ok
                            ? Bundle.OpsHub_stepOk(stepName(step)) : Bundle.OpsHub_stepFailed(stepName(step)));
                    fireState();
                });
            }
        };
    }

    // ---- rollback ----

    /**
     * What going back would do, or why it cannot be done.
     *
     * @param plan the run and verify lines with the earlier stamp; null when refused
     * @param fromStamp the stamp running now
     * @param refusal the sentence to show; null when the offer stands
     */
    public record RollbackOffer(DeployPlan plan, String fromStamp, String refusal) {

        public boolean ok() {
            return refusal == null;
        }

        static RollbackOffer refused(String why) {
            return new RollbackOffer(null, null, why);
        }
    }

    /**
     * Answers, on the EDT, with what rolling a deployed service back
     * would run — after asking the HOST which images of it it still
     * holds — or with the reason it cannot. Everything spliced into a
     * remote line is validated here first: the app is the container's
     * label, which is the host's word, and a label that is not a slug is
     * refused as "not ours" rather than quoted.
     */
    public void rollbackOffer(OpsNode service, Consumer<RollbackOffer> then) {
        OpsNode host = service == null || service.hostId == null ? null : topology.node(service.hostId);
        if (service == null || service.kind != OpsKind.SERVICE || host == null) {
            then.accept(RollbackOffer.refused(Bundle.OpsHub_refusePickHost()));
            return;
        }
        String app = service.app;
        if (app == null || !DeployPlan.SLUG.matcher(app).matches()) {
            then.accept(RollbackOffer.refused(Bundle.OpsHub_refuseNotOurs(service.label)));
            return;
        }
        SshTarget target = targetOf(host);
        if (target == null || !host.status.reachable()) {
            then.accept(RollbackOffer.refused(Bundle.OpsHub_refuseNotReachable(host.label)));
            return;
        }
        DeployRun running = deploys.get(host.id);
        if (running != null && !running.finished) {
            then.accept(RollbackOffer.refused(Bundle.OpsHub_refuseDeployRunning(host.label)));
            return;
        }
        String current = org.nmox.studio.infra.ops.remote.Rollback.stampOf(service.facts.get("image"), app);
        if (current == null) {
            // ours by its label, but running an image this product did not tag: say that, not "not deployed from here"
            then.accept(RollbackOffer.refused(Bundle.OpsHub_refuseForeignImage(service.label,
                    String.valueOf(service.facts.get("image")))));
            return;
        }
        int[] ports = new HostFacts.ContainerFact("", "", "", "", service.facts.get("ports"), Map.of(), List.of()).firstMapping();
        if (ports == null) {
            then.accept(RollbackOffer.refused(Bundle.OpsHub_refuseNoPorts(service.label)));
            return;
        }
        event(OpsEvent.Kind.INFO, service.id, Bundle.OpsHub_readingImages(app, host.label));
        RemoteRunner runner = runnerFor(host);
        onSsh(() -> {
            List<String> lines = Collections.synchronizedList(new ArrayList<>());
            RemoteRunner.Result r = runner.run(target, org.nmox.studio.infra.ops.remote.Rollback.imagesCommand(app),
                    null, lines::add, java.time.Duration.ofSeconds(20));
            RollbackOffer offer;
            if (!r.ok()) {
                String said = lines.isEmpty() ? r.tail() : lines.get(lines.size() - 1);
                offer = RollbackOffer.refused(Bundle.OpsHub_refuseImagesUnread(app, host.label, said == null ? "" : said));
            } else {
                String previous = org.nmox.studio.infra.ops.remote.Rollback.previous(current, new ArrayList<>(lines));
                offer = previous == null
                        ? RollbackOffer.refused(Bundle.OpsHub_refuseNoEarlier(app, current))
                        : new RollbackOffer(new DeployPlan(app, ports[0], ports[1], previous), current, null);
            }
            edt.accept(() -> then.accept(offer));
        });
    }

    /**
     * Starts the earlier image: the run is observable through {@link #deployOf} like a deploy,
     * and the same one-run-per-host rule refuses a second.
     */
    public DeployRun rollback(OpsNode host, DeployPlan plan, String fromStamp) {
        String refusal = host == null || host.kind != OpsKind.HOST ? Bundle.OpsHub_refusePickHost()
                : targetOf(host) == null || !host.status.reachable() ? Bundle.OpsHub_refuseNotReachable(host.label)
                : deploys.get(host.id) != null && !deploys.get(host.id).finished ? Bundle.OpsHub_refuseDeployRunning(host.label)
                : null;
        if (refusal != null) {
            event(OpsEvent.Kind.FAIL, host == null ? null : host.id, Bundle.OpsHub_rollbackRefused(refusal));
            return null;
        }
        DeployRun run = new DeployRun(host.id, plan, clock.getAsLong(), true);
        deploys.put(host.id, run);
        fireState();
        event(OpsEvent.Kind.STEP, host.id, Bundle.OpsHub_rollingBack(plan.app(), host.label,
                fromStamp == null ? "?" : fromStamp, plan.stamp()));
        SshTarget target = targetOf(host);
        RemoteRunner runner = runnerFor(host);
        onSsh(() -> {
            Deployer.Outcome outcome = new Deployer(runner).rollback(plan, target, tracker(run, host));
            edt.accept(() -> {
                run.outcome = outcome;
                run.finished = true;
                if (outcome.ok()) {
                    event(OpsEvent.Kind.OK, host.id, Bundle.OpsHub_rolledBack(plan.app(), host.label, plan.stamp(), httpWord(outcome)));
                } else {
                    event(OpsEvent.Kind.FAIL, host.id, Bundle.OpsHub_rollbackStopped(stepName(outcome.failedAt()), reasonOf(outcome)));
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
        event(kind, nodeId, text, false);
    }

    /** An event that is the host's own answer over ssh: the HUD runs a pulse along its link for these alone. */
    private void linkEvent(OpsEvent.Kind kind, String nodeId, String text) {
        event(kind, nodeId, text, true);
    }

    private void event(OpsEvent.Kind kind, String nodeId, String text, boolean overLink) {
        OpsEvent e = new OpsEvent(clock.getAsLong(), kind, nodeId, text, overLink);
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

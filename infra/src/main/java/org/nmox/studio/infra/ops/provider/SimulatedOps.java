package org.nmox.studio.infra.ops.provider;

import java.util.ArrayList;
import java.util.List;

/**
 * The simulation as a provider: a menu that looks like a cloud's, hosts
 * that come up on a schedule, no network and no bill. It exists so the
 * Create → Connect → Deploy loop can be seen and tested without an
 * account, and it says what it is: {@link #simulated()} is true, every
 * host it makes is badged, and nothing starts it but the user's own
 * switch in the Hub.
 *
 * <p>Deliberately NOT registered through Lookup: the Hub holds the one
 * instance behind its simulation switch, so a provider list never offers
 * the simulation as if it were a cloud.
 */
public final class SimulatedOps implements OpsProvider {

    public static final String ID = "simulation";

    private final SimulatedWorld world;
    private final String name;

    public SimulatedOps(SimulatedWorld world) {
        this(world, "Simulation");
    }

    /**
     * @param name what the simulation is called wherever a provider is named ("listed by …",
     *             "Create … at …"): the Hub hands it the reader's word for it, the one on its checkbox
     */
    public SimulatedOps(SimulatedWorld world, String name) {
        this.world = world;
        this.name = name;
    }

    public SimulatedWorld world() {
        return world;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return name;
    }

    @Override
    public boolean simulated() {
        return true;
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public ProviderOptions options() {
        return new ProviderOptions(
                List.of(new ProviderOptions.Region("sim1", "Simulated region 1", true),
                        new ProviderOptions.Region("sim2", "Simulated region 2", true)),
                List.of(new ProviderOptions.Size("s-1vcpu-1gb", 6.0, 0.00893, 1, 1024, 25, List.of("sim1", "sim2")),
                        new ProviderOptions.Size("s-2vcpu-2gb", 18.0, 0.02679, 2, 2048, 60, List.of("sim1", "sim2"))),
                List.of(new ProviderOptions.Image("ubuntu-24-04-x64", "24.04 (LTS) x64", "Ubuntu")),
                List.of(new ProviderOptions.SshKey("sim-key", "this machine (simulated)", "SHA256:simulated")),
                "USD");
    }

    @Override
    public List<HostRecord> listHosts() {
        List<HostRecord> out = new ArrayList<>();
        for (SimulatedWorld.SimHost h : world.list()) {
            out.add(world.record(h));
        }
        return out;
    }

    @Override
    public HostRecord createHost(HostSpec spec) {
        return world.record(world.create(spec));
    }

    @Override
    public HostRecord readHost(String providerId) {
        SimulatedWorld.SimHost h = world.byId(providerId);
        return h == null ? null : world.record(h);
    }

    @Override
    public void destroyHost(String providerId) {
        world.destroy(providerId);
    }

    @Override
    public String addSshKey(String name, String publicKey) {
        return "sim-key";
    }
}

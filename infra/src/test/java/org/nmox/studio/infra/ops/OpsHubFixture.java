package org.nmox.studio.infra.ops;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.nmox.studio.infra.ops.model.OpsNode;
import org.nmox.studio.infra.ops.remote.SshTarget;

/**
 * A Hub with everything asynchronous held still, for tests outside this
 * package (the HUD's): an inline EDT, lanes that queue until drained, a
 * memory preference node, a runner that answers what it is told. Built on
 * the same pieces {@link OpsHubTest} uses, so the two cannot drift.
 */
public final class OpsHubFixture {

    public final AtomicLong clock = new AtomicLong(1_700_000_000_000L);
    private final OpsHubTest.ManualLane cloud = new OpsHubTest.ManualLane(clock);
    private final OpsHubTest.ManualLane ssh = new OpsHubTest.ManualLane(clock);
    private final OpsHubTest.FixedRunner runner = new OpsHubTest.FixedRunner();
    public final OpsHub hub = new OpsHub(clock::get, Runnable::run, runner,
            new OpsHubTest.MemoryPrefs(null, ""), cloud, ssh).withProviders(List.of());

    /** Adds a host the way Add Host… does; it has an address and no probe answer yet. */
    public OpsNode host(String label, int lastOctet) {
        return hub.addManualHost(new SshTarget("root", "203.0.113." + lastOctet, 22, null), label);
    }

    /** What every host answers a probe with from now on: a complete probe carrying these container lines. */
    public void answer(String... containerLines) {
        runner.lines = OpsHubTest.probe(containerLines);
    }

    /** Says whether this machine has mosh, so no test looks at the real PATH. */
    public void mosh(boolean present) {
        hub.moshOnPath(present);
    }

    /** Runs everything queued on both lanes. */
    public void drain() {
        for (int i = 0; i < 4; i++) {
            cloud.drain(2);
            ssh.drain(2);
        }
    }

    public static String container(String name, String image, String ports, String labels) {
        return "container {\"Names\":\"" + name + "\",\"Image\":\"" + image + "\",\"Status\":\"Up\",\"Ports\":\""
                + ports + "\",\"Labels\":\"" + labels + "\",\"Networks\":\"bridge\"}";
    }
}

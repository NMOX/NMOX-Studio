package org.nmox.studio.infra.ops.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One thing on the topology. Identity is the {@code id}; everything else
 * is what the Hub has observed about it so far. A host's id is stable
 * across polls ({@code do:123}, {@code manual:user@host:22},
 * {@code sim:3}); a service's id is its host's id plus the container name,
 * so a redeploy keeps the same node and the HUD keeps its place.
 *
 * <p>Mutable, and EDT-confined once it is in a {@link Topology}: workers
 * observe off the paint thread and hand results to the controller, which
 * applies them on the EDT (the Infra Designer's ledger-53d rule).
 */
public final class OpsNode {

    public final String id;
    public final OpsKind kind;
    /** What the HUD prints under the glyph. */
    public String label;
    /** Which provider owns it ({@code digitalocean}, {@code simulation}, {@code manual}); null for the workstation. */
    public String provider;
    /** True on a node that exists only in the simulation — the HUD badges it. */
    public boolean simulated;
    /** The provider's own id for the resource, when it has one. */
    public String providerId;
    /** Host: the ssh user, address and port; null on other kinds. */
    public String sshUser;
    public String address;
    public int sshPort = 22;
    /** Host: an identity file to pass to ssh, or null for the agent/config default. */
    public String identityFile;
    public HostStatus status = HostStatus.READY;
    /** Service: the host it runs on. */
    public String hostId;
    /**
     * Service: the app this product deployed it as (the container's {@code nmox.ops.app} label),
     * or null for a neighbour. It is the HOST's word — validate it before it nears a shell.
     */
    public String app;
    /** Facts the inspector shows, in insertion order (region, size, image, ports…). Values are plain text. */
    public final Map<String, String> facts = new LinkedHashMap<>();
    /** Host measurements; empty for other kinds. */
    public final Telemetry telemetry = new Telemetry();
    /** When the Hub last heard from it (a probe answered, the provider listed it), epoch millis; 0 never. */
    public long lastSeenMillis;
    /** The last refusal or error about this node, for the inspector; null when none. */
    public String lastProblem;

    public OpsNode(String id, OpsKind kind, String label) {
        this.id = Objects.requireNonNull(id);
        this.kind = Objects.requireNonNull(kind);
        this.label = label == null ? id : label;
    }

    /** {@code user@host} for an ssh host, the port appended when it is not 22. */
    public String sshSpec() {
        if (address == null) {
            return null;
        }
        String base = (sshUser == null ? "root" : sshUser) + "@" + address;
        return sshPort == 22 ? base : base + ":" + sshPort;
    }

    @Override
    public String toString() {
        return kind + " " + id + " (" + label + ")";
    }
}

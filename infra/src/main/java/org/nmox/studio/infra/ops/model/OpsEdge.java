package org.nmox.studio.infra.ops.model;

/**
 * A relationship between two nodes, with how it is known. {@code kind} is a
 * short machine word the HUD maps to a style ({@code runs} host→service,
 * {@code reaches} service→service over a shared network, {@code depends}
 * from a compose file, {@code ssh} workstation→host, {@code vpc} host↔host).
 */
public record OpsEdge(String from, String to, String kind, Evidence evidence, String source) {

    public OpsEdge {
        if (from == null || to == null || kind == null || evidence == null) {
            throw new IllegalArgumentException("an edge needs both ends, a kind and its evidence");
        }
        if (source == null) {
            source = "";
        }
    }

    /** Edges are identified by ends and kind; evidence and source are properties. */
    public String key() {
        return from + ">" + to + ":" + kind;
    }
}

package org.nmox.studio.infra.ops.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The Hub's one picture of everything it knows: nodes by id, edges by key,
 * insertion-ordered so the HUD's layout is deterministic. Listeners hear
 * about structural changes (a node added or removed, an edge replaced) and
 * about facts changing on a node; the canvas repaints on either.
 *
 * <p>EDT-confined after construction — see {@link OpsNode}.
 */
public final class Topology {

    /** The workstation node's fixed id. */
    public static final String WORKSTATION_ID = "workstation";

    public interface Listener {
        /** Nodes or edges came or went. */
        void structureChanged();

        /** Something about one node changed (status, facts, telemetry). */
        void nodeChanged(OpsNode node);
    }

    private final Map<String, OpsNode> nodes = new LinkedHashMap<>();
    private final Map<String, OpsEdge> edges = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    public Topology() {
        OpsNode me = new OpsNode(WORKSTATION_ID, OpsKind.WORKSTATION, "this machine");
        nodes.put(me.id, me);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public OpsNode workstation() {
        return nodes.get(WORKSTATION_ID);
    }

    public OpsNode node(String id) {
        return nodes.get(id);
    }

    public List<OpsNode> nodes() {
        return new ArrayList<>(nodes.values());
    }

    public List<OpsNode> nodes(OpsKind kind) {
        List<OpsNode> out = new ArrayList<>();
        for (OpsNode n : nodes.values()) {
            if (n.kind == kind) {
                out.add(n);
            }
        }
        return out;
    }

    public List<OpsEdge> edges() {
        return new ArrayList<>(edges.values());
    }

    /** The services observed on a host, in the order they were seen. */
    public List<OpsNode> servicesOf(String hostId) {
        List<OpsNode> out = new ArrayList<>();
        for (OpsNode n : nodes.values()) {
            if (n.kind == OpsKind.SERVICE && hostId.equals(n.hostId)) {
                out.add(n);
            }
        }
        return out;
    }

    /** Edges touching a node, either end. */
    public List<OpsEdge> edgesOf(String id) {
        List<OpsEdge> out = new ArrayList<>();
        for (OpsEdge e : edges.values()) {
            if (e.from().equals(id) || e.to().equals(id)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Adds or replaces (by id); returns the node now in the topology. */
    public OpsNode put(OpsNode node) {
        OpsNode previous = nodes.put(node.id, node);
        if (previous == null) {
            fireStructure();
        } else {
            fireNode(node);
        }
        return node;
    }

    /** Removes a node and every edge touching it; a service's host stays. */
    public boolean remove(String id) {
        if (WORKSTATION_ID.equals(id) || nodes.remove(id) == null) {
            return false;
        }
        edges.values().removeIf(e -> e.from().equals(id) || e.to().equals(id));
        // a host takes its observed services with it: they have nowhere to run
        List<String> orphans = new ArrayList<>();
        for (OpsNode n : nodes.values()) {
            if (n.kind == OpsKind.SERVICE && id.equals(n.hostId)) {
                orphans.add(n.id);
            }
        }
        for (String orphan : orphans) {
            nodes.remove(orphan);
            edges.values().removeIf(e -> e.from().equals(orphan) || e.to().equals(orphan));
        }
        fireStructure();
        return true;
    }

    /** Adds or replaces an edge by its key (ends + kind). */
    public void connect(OpsEdge edge) {
        if (!nodes.containsKey(edge.from()) || !nodes.containsKey(edge.to())) {
            throw new IllegalArgumentException("edge names a node the topology does not hold: " + edge);
        }
        OpsEdge previous = edges.put(edge.key(), edge);
        if (previous == null || previous.evidence() != edge.evidence()) {
            fireStructure();
        }
    }

    /**
     * Replaces every edge matching the predicate with the given ones in one
     * structural change — what a probe does with a host's {@code runs},
     * {@code reaches} and {@code depends} edges after each observation.
     */
    public void replaceEdges(Predicate<OpsEdge> which, Collection<OpsEdge> replacements) {
        boolean changed = edges.values().removeIf(which);
        for (OpsEdge e : replacements) {
            if (nodes.containsKey(e.from()) && nodes.containsKey(e.to())) {
                changed |= edges.put(e.key(), e) == null;
            }
        }
        if (changed) {
            fireStructure();
        }
    }

    /** Says a node's facts changed without changing the structure. */
    public void touched(OpsNode node) {
        fireNode(node);
    }

    private void fireStructure() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.structureChanged();
        }
    }

    private void fireNode(OpsNode node) {
        for (Listener l : new ArrayList<>(listeners)) {
            l.nodeChanged(node);
        }
    }
}

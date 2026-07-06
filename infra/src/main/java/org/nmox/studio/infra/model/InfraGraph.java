package org.nmox.studio.infra.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The design: nodes placed on the canvas and the wires between them.
 * Wires are validated against the catalog's relationship rules, so an
 * impossible topology can't be drawn in the first place.
 */
public final class InfraGraph {

    /** One placed resource. */
    public static final class InfraNode {

        public final String id;
        public final NodeKind kind;
        public int x;
        public int y;
        public String label;
        public final Map<String, String> props = new LinkedHashMap<>();
        /** DigitalOcean resource id once deployed; null while design-only. */
        public String doId;
        /** Public IPv4 once known (droplets); null otherwise. */
        public String ip;
        public String status = "";

        InfraNode(String id, NodeKind kind, int x, int y) {
            this.id = id;
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.label = kind.getDisplayName().toLowerCase().replace(" ", "-");
            for (NodeKind.Prop p : kind.getProps()) {
                props.put(p.key(), p.defaultValue());
            }
        }

        public double monthlyUsd() {
            return kind.estimateMonthlyUsd(props);
        }
    }

    /** A relationship wire from one node's output to another's input. */
    public record Wire(String fromId, String toId) {
    }

    public interface Listener {
        default void graphChanged() { }
        default void nodeStatusChanged(InfraNode node) { }
    }

    private final Map<String, InfraNode> nodes = new LinkedHashMap<>();
    private final List<Wire> wires = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    public synchronized InfraNode addNode(NodeKind kind, int x, int y) {
        String id = kind.name().toLowerCase() + "-" + sequence.incrementAndGet();
        InfraNode node = new InfraNode(id, kind, x, y);
        nodes.put(id, node);
        fireChanged();
        return node;
    }

    /** Restores a node with a known id (persistence/sync). */
    public synchronized InfraNode restoreNode(String id, NodeKind kind, int x, int y) {
        InfraNode node = new InfraNode(id, kind, x, y);
        nodes.put(id, node);
        long numeric = extractSequence(id);
        sequence.updateAndGet(current -> Math.max(current, numeric));
        return node;
    }

    private static long extractSequence(String id) {
        int dash = id.lastIndexOf('-');
        try {
            return Long.parseLong(id.substring(dash + 1));
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    public synchronized void removeNode(InfraNode node) {
        if (nodes.remove(node.id) != null) {
            wires.removeIf(w -> w.fromId().equals(node.id) || w.toId().equals(node.id));
            fireChanged();
        }
    }

    public synchronized InfraNode node(String id) {
        return nodes.get(id);
    }

    public synchronized List<InfraNode> getNodes() {
        return Collections.unmodifiableList(new ArrayList<>(nodes.values()));
    }

    public synchronized List<Wire> getWires() {
        return Collections.unmodifiableList(new ArrayList<>(wires));
    }

    /** Whether a wire from -> to is allowed by the catalog rules. */
    public boolean canConnect(InfraNode from, InfraNode to) {
        return from != null && to != null && from != to
                && from.kind.wiresInto().contains(to.kind);
    }

    /** Adds the wire if valid and not a duplicate; returns success. */
    public synchronized boolean connect(InfraNode from, InfraNode to) {
        if (!canConnect(from, to)) {
            return false;
        }
        Wire wire = new Wire(from.id, to.id);
        if (wires.contains(wire)) {
            return false;
        }
        wires.add(wire);
        fireChanged();
        return true;
    }

    public synchronized void disconnect(Wire wire) {
        if (wires.remove(wire)) {
            fireChanged();
        }
    }

    /** Wires arriving at this node (its providers). */
    public synchronized List<InfraNode> providersOf(InfraNode node) {
        List<InfraNode> result = new ArrayList<>();
        for (Wire w : wires) {
            if (w.toId().equals(node.id)) {
                InfraNode from = nodes.get(w.fromId());
                if (from != null) {
                    result.add(from);
                }
            }
        }
        return result;
    }

    public synchronized void clear() {
        // idempotent: clearing an already-empty graph must not fire a change.
        // A spurious graphChanged here (e.g. from load() on a fresh launch with
        // no design file) schedules a needless save that writes .nmoxinfra.json
        // into the workspace and churns the canvas repaint listener.
        if (nodes.isEmpty() && wires.isEmpty()) {
            return;
        }
        nodes.clear();
        wires.clear();
        fireChanged();
    }

    /** Estimated total monthly cost of the whole design. */
    public synchronized double totalMonthlyUsd() {
        return nodes.values().stream().mapToDouble(InfraNode::monthlyUsd).sum();
    }

    public void setStatus(InfraNode node, String status) {
        // equality-guarded (house storm law): cloud sync re-sets "live" on
        // every node every Refresh — an unconditional fire would re-paint
        // and re-notify per node per pass for a change that isn't one.
        if (java.util.Objects.equals(node.status, status)) {
            return;
        }
        node.status = status;
        for (Listener l : listeners) {
            l.nodeStatusChanged(node);
        }
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** How many listeners are attached — a lifecycle-test seam, not an API. */
    public int listenerCount() {
        return listeners.size();
    }

    /** Public nudge after direct field edits (node drag, property change). */
    public void touch() {
        fireChanged();
    }

    void fireChanged() {
        for (Listener l : listeners) {
            l.graphChanged();
        }
    }
}

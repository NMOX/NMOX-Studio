package org.nmox.studio.infra.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;

/**
 * The links to live cloud resources the canvas holds and its design file
 * does not (3.4). A deploy or a sync writes a node's {@code doId} on the
 * canvas and then saves; when that save finds somebody else's bytes on disk
 * it writes nothing and the design reloads — and before this class, the
 * reload replaced the canvas and every {@code doId} the operation had just
 * written with it. The resources went on existing and billing, with nothing
 * in the design to find or destroy them by.
 *
 * <p>So a link is never dropped silently. Before a reload it is captured;
 * after it, it goes back onto the node it belonged to when that node is
 * still there and the design may be written; otherwise it stays pending and
 * is said once. It leaves only when a write that carries it lands, or when
 * the file on disk turns out to carry it already. EDT-confined, like the
 * graph it reads.
 */
public final class PendingCloudLinks {

    /** One node's link to a live resource, and the design file it belongs to. */
    public record Link(File file, String nodeId, String kind, String label,
            String provider, String doId, String ip) {

        public Link {
            Objects.requireNonNull(file);
            Objects.requireNonNull(nodeId);
            Objects.requireNonNull(doId);
        }

        boolean sameNode(Link other) {
            return file.equals(other.file) && nodeId.equals(other.nodeId);
        }
    }

    /** What a reload did with the pending links of its file. */
    public record Reapplied(List<Link> applied, List<Link> toSay) {
    }

    private final List<Link> pending = new ArrayList<>();
    /** Links already said, so a link is said once and not once per reload. */
    private final Set<Link> said = new HashSet<>();

    /** node id → doId for every node the graph links to a live resource. Pure. */
    public static Map<String, String> linksOf(InfraGraph graph) {
        Map<String, String> links = new LinkedHashMap<>();
        for (InfraNode node : graph.getNodes()) {
            if (node.doId != null) {
                links.put(node.id, node.doId);
            }
        }
        return links;
    }

    /**
     * The links the canvas holds that {@code saved} (what the design file is
     * known to hold) does not — the ones a reload would lose. Pure.
     */
    public static List<Link> unsaved(InfraGraph graph, Map<String, String> saved, File file) {
        List<Link> out = new ArrayList<>();
        for (InfraNode node : graph.getNodes()) {
            if (node.doId != null && !node.doId.equals(saved.get(node.id))) {
                out.add(new Link(file, node.id, node.kind.getDisplayName(), node.label,
                        node.kind.provider().displayName(), node.doId, node.ip));
            }
        }
        return out;
    }

    /** Holds these links until a write carries them; a node's newer link replaces its older one. */
    public void add(Collection<Link> links) {
        for (Link link : links) {
            pending.removeIf(link::sameNode);
            pending.add(link);
        }
    }

    /**
     * After {@code file} was read into {@code graph}: every pending link of
     * that file goes back onto its node when the design may be written, the
     * node is still there and it links to nothing else. A node already
     * carrying the same id means the file holds it — the link is settled.
     * Everything else stays pending, and what has not been said yet is
     * returned to be said (links of other files included: they are just as
     * unsaved while another design is shown).
     */
    public Reapplied reapply(InfraGraph graph, File file, boolean writable) {
        List<Link> applied = new ArrayList<>();
        List<Link> toSay = new ArrayList<>();
        for (Link link : new ArrayList<>(pending)) {
            if (link.file().equals(file)) {
                InfraNode node = graph.node(link.nodeId());
                if (node != null && link.doId().equals(node.doId)) {
                    pending.remove(link); // the file already carries it
                    continue;
                }
                if (writable && node != null && node.doId == null) {
                    node.doId = link.doId();
                    if (node.ip == null) {
                        node.ip = link.ip();
                    }
                    applied.add(link);
                    continue;
                }
            }
            if (said.add(link)) {
                toSay.add(link);
            }
        }
        return new Reapplied(applied, toSay);
    }

    /** A write of {@code file} carrying these resource ids landed: the links it carried are saved. */
    public void landed(File file, Collection<String> writtenIds) {
        Set<String> ids = new HashSet<>(writtenIds);
        pending.removeIf(link -> link.file().equals(file) && ids.contains(link.doId()));
    }

    /** The links still waiting for a write, oldest first. */
    public List<Link> pending() {
        return List.copyOf(pending);
    }
}

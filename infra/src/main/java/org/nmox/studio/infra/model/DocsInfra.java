package org.nmox.studio.infra.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.nmox.studio.core.spi.DocsScene;
import org.nmox.studio.core.util.DocsFixtures;
import org.openide.util.lookup.ServiceProvider;

/**
 * Stages the infrastructure design the guides photograph (v2.163.0).
 *
 * <p>An empty canvas illustrates nothing, so the picture has always shown a
 * worked design — and it showed it in English to every reader. The design is
 * written here through the product's own {@link GraphIO}, so it cannot drift
 * from the format the designer reads, and the NODE KINDS keep whatever each
 * language's bundle calls them ({@code Droplet} and {@code Volume} stay
 * English on purpose — they are DigitalOcean's product names). Only the
 * LABELS are fixture content, because a label is what the user typed.
 *
 * <p>The shape is the smallest honest one: storage feeds a server, the
 * server sits behind a load balancer, and the load balancer answers for a
 * domain. Every wire is legal by {@link NodeKind}'s own rules, so the
 * picture shows a design the product would actually let you draw.
 */
@ServiceProvider(service = DocsScene.class)
public final class DocsInfra implements DocsScene {

    /** The scene's name, as its picture is named. */
    public static final String ID = "infra-designer";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public File stage(File home, String fixtures, String lang) throws IOException {
        File dir = DocsFixtures.projectDir(home);
        Files.createDirectories(dir.toPath());

        InfraGraph graph = new InfraGraph();
        InfraGraph.InfraNode volume = node(graph, NodeKind.VOLUME, 80, 250, DocsFixtures.text(fixtures, lang, "infra", "volume"));
        InfraGraph.InfraNode droplet = node(graph, NodeKind.DROPLET, 320, 250, DocsFixtures.text(fixtures, lang, "infra", "droplet"));
        InfraGraph.InfraNode balancer = node(graph, NodeKind.LOAD_BALANCER, 560, 150, DocsFixtures.text(fixtures, lang, "infra", "loadBalancer"));
        InfraGraph.InfraNode domain = node(graph, NodeKind.DOMAIN, 800, 150, DocsFixtures.text(fixtures, lang, "infra", "dns"));

        graph.connect(volume, droplet);
        graph.connect(droplet, balancer);
        graph.connect(balancer, domain);

        GraphIO.save(graph, new File(dir, GraphIO.DEFAULT_FILENAME));
        return dir;
    }

    private static InfraGraph.InfraNode node(InfraGraph graph, NodeKind kind, int x, int y, String label) {
        InfraGraph.InfraNode n = graph.addNode(kind, x, y);
        n.label = label;
        return n;
    }
}

package org.nmox.studio.infra.model;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.DocsFixtures;

import static org.assertj.core.api.Assertions.assertThat;

/** The design every language's Infra Designer picture shows, read back through GraphIO. */
class DocsInfraTest {

    private static final String FIXTURES = """
            {"en": {"infra": {"dns": "shop.example.com", "loadBalancer": "Front door",
                              "droplet": "App server", "volume": "Order archive"}}}
            """;

    private static InfraGraph staged(Path home) throws Exception {
        File dir = new DocsInfra().stage(home.toFile(), FIXTURES, "en");
        assertThat(dir).isEqualTo(DocsFixtures.projectDir(home.toFile()));
        InfraGraph graph = new InfraGraph();
        GraphIO.load(graph, new File(dir, GraphIO.DEFAULT_FILENAME));
        return graph;
    }

    @Test
    @DisplayName("four nodes carry the fixture's labels on the right kinds")
    void nodesCarryTheLabels(@TempDir Path home) throws Exception {
        List<InfraGraph.InfraNode> nodes = staged(home).getNodes();
        assertThat(nodes).extracting(n -> n.kind + "=" + n.label).containsExactlyInAnyOrder(
                "VOLUME=Order archive", "DROPLET=App server",
                "LOAD_BALANCER=Front door", "DOMAIN=shop.example.com");
    }

    @Test
    @DisplayName("every wire is one the designer itself would let a user draw")
    void everyWireIsLegal(@TempDir Path home) throws Exception {
        InfraGraph graph = staged(home);
        assertThat(graph.getWires()).hasSize(3);
        for (InfraGraph.Wire w : graph.getWires()) {
            assertThat(graph.canConnect(graph.node(w.fromId()), graph.node(w.toId())))
                    .as(w + " must be legal by NodeKind's own rules").isTrue();
        }
    }
}

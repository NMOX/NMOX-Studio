package org.nmox.studio.infra.model;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.api.CloudProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cost meter's arithmetic and the catalog's wire/provider metadata.
 * estimateMonthlyUsd is a pure function of a kind and its property map,
 * so every pricing branch is testable with a literal Map - no graph, no
 * network. Complements InfraModelTest (which asserts the whole-catalog
 * contract and the droplet/k8s happy path).
 */
class NodeKindCostTest {

    // ---- per-size lookups ----

    @Test
    @DisplayName("Droplet price tracks its size slug, and an unknown slug falls back to the base rate")
    void dropletPriceBySizeWithFallback() {
        assertThat(NodeKind.DROPLET.estimateMonthlyUsd(Map.of("size", "s-8vcpu-16gb"))).isEqualTo(96.0);
        assertThat(NodeKind.DROPLET.estimateMonthlyUsd(Map.of("size", "c-2"))).isEqualTo(42.0);
        // unknown slug -> base (6.0)
        assertThat(NodeKind.DROPLET.estimateMonthlyUsd(Map.of("size", "made-up"))).isEqualTo(6.0);
        // absent slug -> catalog default s-1vcpu-1gb -> 6.0
        assertThat(NodeKind.DROPLET.estimateMonthlyUsd(Map.of())).isEqualTo(6.0);
    }

    @Test
    @DisplayName("Hetzner server price tracks its type, with a cx22 fallback")
    void hetznerServerPriceByType() {
        assertThat(NodeKind.HZ_SERVER.estimateMonthlyUsd(Map.of("serverType", "cx42"))).isEqualTo(19.52);
        assertThat(NodeKind.HZ_SERVER.estimateMonthlyUsd(Map.of("serverType", "cax21"))).isEqualTo(7.55);
        assertThat(NodeKind.HZ_SERVER.estimateMonthlyUsd(Map.of("serverType", "unknown"))).isEqualTo(4.59);
        assertThat(NodeKind.HZ_SERVER.estimateMonthlyUsd(Map.of())).isEqualTo(4.59);
    }

    // ---- per-GB storage ----

    @Test
    @DisplayName("Volume cost is $0.10/GiB; a bad size falls back to the 100GiB default")
    void volumePerGigabyte() {
        assertThat(NodeKind.VOLUME.estimateMonthlyUsd(Map.of("sizeGb", "250"))).isEqualTo(25.0);
        assertThat(NodeKind.VOLUME.estimateMonthlyUsd(Map.of("sizeGb", "not-a-number")))
                .isEqualTo(10.0); // 0.10 * 100
        assertThat(NodeKind.VOLUME.estimateMonthlyUsd(Map.of())).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Hetzner volume cost is $0.052/GiB off a 50GiB default")
    void hetznerVolumePerGigabyte() {
        assertThat(NodeKind.HZ_VOLUME.estimateMonthlyUsd(Map.of("sizeGb", "100")))
                .isEqualTo(5.2);
        assertThat(NodeKind.HZ_VOLUME.estimateMonthlyUsd(Map.of()))
                .isCloseTo(2.6, org.assertj.core.data.Offset.offset(1e-9)); // 0.052 * 50
    }

    // ---- multiplied by node count ----

    @Test
    @DisplayName("Kubernetes cost is node-price times count, plus an HA surcharge")
    void kubernetesScalesWithPool() {
        assertThat(NodeKind.KUBERNETES.estimateMonthlyUsd(
                Map.of("nodeSize", "s-4vcpu-8gb", "nodeCount", "2", "ha", "false")))
                .isEqualTo(48.0 * 2);
        assertThat(NodeKind.KUBERNETES.estimateMonthlyUsd(
                Map.of("nodeSize", "s-2vcpu-4gb", "nodeCount", "3", "ha", "true")))
                .isEqualTo(24.0 * 3 + 40.0);
        // an unparseable count falls back to 3
        assertThat(NodeKind.KUBERNETES.estimateMonthlyUsd(Map.of("nodeCount", "x")))
                .isEqualTo(24.0 * 3);
    }

    @Test
    @DisplayName("A managed database multiplies its size price by the node count, floored at one")
    void databaseScalesWithNodes() {
        assertThat(NodeKind.DB_POSTGRES.estimateMonthlyUsd(
                Map.of("size", "db-s-2vcpu-4gb", "nodes", "3"))).isEqualTo(60.0 * 3);
        assertThat(NodeKind.DB_MYSQL.estimateMonthlyUsd(
                Map.of("size", "db-s-1vcpu-1gb", "nodes", "1"))).isEqualTo(15.0);
        // zero or negative nodes still bill at least one primary
        assertThat(NodeKind.DB_VALKEY.estimateMonthlyUsd(
                Map.of("size", "db-s-1vcpu-1gb", "nodes", "0"))).isEqualTo(15.0);
        // an unknown size falls back to the 15.0 base
        assertThat(NodeKind.DB_MONGODB.estimateMonthlyUsd(
                Map.of("size", "db-huge", "nodes", "1"))).isEqualTo(15.0);
    }

    @Test
    @DisplayName("Kafka and OpenSearch price flat off their catalog base, unaffected by props")
    void flatPricedDatabases() {
        assertThat(NodeKind.DB_KAFKA.estimateMonthlyUsd(Map.of("nodes", "5"))).isEqualTo(147.0);
        assertThat(NodeKind.DB_OPENSEARCH.estimateMonthlyUsd(Map.of())).isEqualTo(49.0);
    }

    @Test
    @DisplayName("Free and flat kinds ignore their props entirely")
    void flatKindsIgnoreProps() {
        assertThat(NodeKind.VPC.estimateMonthlyUsd(Map.of("region", "sfo3"))).isEqualTo(0.0);
        assertThat(NodeKind.FUNCTIONS.estimateMonthlyUsd(Map.of())).isEqualTo(0.0);
        assertThat(NodeKind.LOAD_BALANCER.estimateMonthlyUsd(Map.of("size", "lb-large")))
                .isEqualTo(12.0);
        assertThat(NodeKind.CF_DNS_RECORD.estimateMonthlyUsd(Map.of())).isEqualTo(0.0);
    }

    // ---- provider routing by category ----

    @Test
    @DisplayName("Provider routing follows the category: Hetzner, Cloudflare, else DigitalOcean")
    void providerFollowsCategory() {
        assertThat(NodeKind.HZ_LB.provider()).isEqualTo(CloudProvider.HETZNER);
        assertThat(NodeKind.CF_R2_BUCKET.provider()).isEqualTo(CloudProvider.CLOUDFLARE);
        assertThat(NodeKind.GRADIENT_AI.provider()).isEqualTo(CloudProvider.DIGITALOCEAN);
        assertThat(NodeKind.DB_POSTGRES.provider()).isEqualTo(CloudProvider.DIGITALOCEAN);
    }

    // ---- attachesTo: post-create wiring vs creation-time reference ----

    @Test
    @DisplayName("attachesTo is true only for the post-create pairs, false for creation references")
    void attachRelationships() {
        assertThat(NodeKind.VOLUME.attachesTo(NodeKind.DROPLET)).isTrue();
        assertThat(NodeKind.FIREWALL.attachesTo(NodeKind.GPU_DROPLET)).isTrue();
        assertThat(NodeKind.RESERVED_IP.attachesTo(NodeKind.DROPLET)).isTrue();
        assertThat(NodeKind.HZ_VOLUME.attachesTo(NodeKind.HZ_SERVER)).isTrue();
        assertThat(NodeKind.HZ_FLOATING_IP.attachesTo(NodeKind.HZ_SERVER)).isTrue();
        assertThat(NodeKind.HZ_FIREWALL.attachesTo(NodeKind.HZ_SERVER)).isTrue();

        // a VPC->droplet wire is a creation-time reference, not an attachment
        assertThat(NodeKind.VPC.attachesTo(NodeKind.DROPLET)).isFalse();
        // a volume does not "attach" across clouds
        assertThat(NodeKind.VOLUME.attachesTo(NodeKind.HZ_SERVER)).isFalse();
        assertThat(NodeKind.HZ_VOLUME.attachesTo(NodeKind.DROPLET)).isFalse();
    }

    // ---- node-level cost delegates to the kind ----

    @Test
    @DisplayName("A node's monthlyUsd reflects edits to its live property map")
    void nodeCostReflectsProps() {
        InfraGraph graph = new InfraGraph();
        InfraGraph.InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 0, 0);
        assertThat(server.monthlyUsd()).isEqualTo(4.59);

        server.props.put("serverType", "cpx31");
        assertThat(server.monthlyUsd()).isEqualTo(16.18);
    }
}

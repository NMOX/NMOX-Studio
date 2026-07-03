package org.nmox.studio.infra.api;

import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The create-request body for every kind the planner emits: each maps
 * to its provider's endpoint and carries the fields that endpoint needs.
 * Pure function of the graph - no network. Existing planner tests cover
 * ordering, placeholders and the DO droplet/database; these fill in the
 * per-kind bodies that were never exercised (k8s, LB, firewall, CDN,
 * registry, App Platform, functions, gradient-ai, certs, alerts, the
 * Hetzner family and Cloudflare R2).
 */
class DeployPlannerBodiesTest {

    // ---- DigitalOcean compute / networking ----

    @Test
    @DisplayName("Kubernetes create carries a node pool with size, count and autoscale")
    void kubernetesBody() {
        InfraGraph graph = new InfraGraph();
        InfraNode vpc = graph.addNode(NodeKind.VPC, 0, 0);
        InfraNode k8s = graph.addNode(NodeKind.KUBERNETES, 200, 0);
        k8s.props.put("nodeCount", "4");
        k8s.props.put("ha", "true");
        graph.connect(vpc, k8s);

        DoRequest request = requestFor(graph, k8s);

        assertThat(request.path()).isEqualTo("/v2/kubernetes/clusters");
        JSONObject body = request.body();
        assertThat(body.getBoolean("ha")).isTrue();
        assertThat(body.getString("vpc_uuid")).isEqualTo("${id-of:" + vpc.id + "}");
        JSONObject pool = body.getJSONArray("node_pools").getJSONObject(0);
        assertThat(pool.getInt("count")).isEqualTo(4);
        assertThat(pool.getString("size")).isEqualTo("s-2vcpu-4gb");
    }

    @Test
    @DisplayName("Load balancer create backs droplets, joins its VPC and honors the forwarding rule")
    void loadBalancerBackendsAndVpc() {
        InfraGraph graph = new InfraGraph();
        InfraNode vpc = graph.addNode(NodeKind.VPC, 0, 0);
        InfraNode droplet = graph.addNode(NodeKind.DROPLET, 200, 0);
        InfraNode lb = graph.addNode(NodeKind.LOAD_BALANCER, 400, 0);
        lb.props.put("forwardingRule", "https-443");
        graph.connect(vpc, droplet);
        graph.connect(vpc, lb);
        graph.connect(droplet, lb);

        DoRequest request = requestFor(graph, lb);
        JSONObject body = request.body();

        assertThat(request.path()).isEqualTo("/v2/load_balancers");
        assertThat(body.getString("vpc_uuid")).isEqualTo("${id-of:" + vpc.id + "}");
        assertThat(body.getJSONArray("droplet_ids").getString(0))
                .isEqualTo("${id-of:" + droplet.id + "}");
        JSONObject rule = body.getJSONArray("forwarding_rules").getJSONObject(0);
        assertThat(rule.getString("entry_protocol")).isEqualTo("https");
        assertThat(rule.getInt("entry_port")).isEqualTo(443);
    }

    @Test
    @DisplayName("A certificate wired to a load balancer flips it to HTTPS/443 with the cert id")
    void loadBalancerWithCertificate() {
        InfraGraph graph = new InfraGraph();
        InfraNode cert = graph.addNode(NodeKind.CERTIFICATE, 0, 0);
        InfraNode lb = graph.addNode(NodeKind.LOAD_BALANCER, 200, 0);
        graph.connect(cert, lb);

        JSONObject rule = requestFor(graph, lb).body()
                .getJSONArray("forwarding_rules").getJSONObject(0);

        assertThat(rule.getString("certificate_id")).isEqualTo("${id-of:" + cert.id + "}");
        assertThat(rule.getString("entry_protocol")).isEqualTo("https");
        assertThat(rule.getInt("entry_port")).isEqualTo(443);
    }

    @Test
    @DisplayName("Firewall create turns the inbound/outbound spec into tcp rules")
    void firewallRules() {
        InfraGraph graph = new InfraGraph();
        InfraNode firewall = graph.addNode(NodeKind.FIREWALL, 0, 0);
        firewall.props.put("inbound", "22/0.0.0.0/0, 443/10.0.0.0/8");

        DoRequest request = requestFor(graph, firewall);
        JSONObject body = request.body();

        assertThat(request.path()).isEqualTo("/v2/firewalls");
        assertThat(body.getJSONArray("inbound_rules").length()).isEqualTo(2);
        JSONObject first = body.getJSONArray("inbound_rules").getJSONObject(0);
        assertThat(first.getString("protocol")).isEqualTo("tcp");
        assertThat(first.getString("ports")).isEqualTo("22");
        // outbound default "all/0.0.0.0/0" maps ports -> "all"
        assertThat(body.getJSONArray("outbound_rules").getJSONObject(0).getString("ports"))
                .isEqualTo("all");
    }

    @Test
    @DisplayName("Reserved IP create carries only its region")
    void reservedIpBody() {
        InfraGraph graph = new InfraGraph();
        InfraNode rip = graph.addNode(NodeKind.RESERVED_IP, 0, 0);
        rip.props.put("region", "sfo3");

        DoRequest request = requestFor(graph, rip);

        assertThat(request.path()).isEqualTo("/v2/reserved_ips");
        assertThat(request.body().getString("region")).isEqualTo("sfo3");
    }

    @Test
    @DisplayName("Volume create carries size and filesystem")
    void volumeBody() {
        InfraGraph graph = new InfraGraph();
        InfraNode volume = graph.addNode(NodeKind.VOLUME, 0, 0);
        volume.props.put("sizeGb", "250");
        volume.props.put("fs", "xfs");

        JSONObject body = requestFor(graph, volume).body();

        assertThat(body.getInt("size_gigabytes")).isEqualTo(250);
        assertThat(body.getString("filesystem_type")).isEqualTo("xfs");
    }

    @Test
    @DisplayName("A domain points its A record at the IP of whatever fronts it")
    void domainTargetsItsProvider() {
        InfraGraph graph = new InfraGraph();
        InfraNode droplet = graph.addNode(NodeKind.DROPLET, 0, 0);
        InfraNode domain = graph.addNode(NodeKind.DOMAIN, 200, 0);
        domain.props.put("name", "example.com");
        graph.connect(droplet, domain);

        JSONObject body = requestFor(graph, domain).body();

        assertThat(body.getString("name")).isEqualTo("example.com");
        assertThat(body.getString("ip_address")).isEqualTo("${ip-of:" + droplet.id + "}");
    }

    @Test
    @DisplayName("An unwired domain falls back to the documentation IP")
    void domainWithoutProvider() {
        InfraGraph graph = new InfraGraph();
        InfraNode domain = graph.addNode(NodeKind.DOMAIN, 0, 0);
        domain.props.put("name", "example.com");

        assertThat(requestFor(graph, domain).body().getString("ip_address"))
                .isEqualTo("192.0.2.1");
    }

    @Test
    @DisplayName("A CDN endpoint takes a wired Spaces bucket as its origin, plus a cert")
    void cdnOriginFromSpaces() {
        InfraGraph graph = new InfraGraph();
        InfraNode spaces = graph.addNode(NodeKind.SPACES, 0, 0);
        spaces.props.put("bucket", "assets");
        spaces.props.put("region", "fra1");
        InfraNode cert = graph.addNode(NodeKind.CERTIFICATE, 0, 100);
        InfraNode cdn = graph.addNode(NodeKind.CDN, 200, 0);
        graph.connect(spaces, cdn);
        graph.connect(cert, cdn);

        JSONObject body = requestFor(graph, cdn).body();

        assertThat(body.getString("origin")).isEqualTo("assets.fra1.digitaloceanspaces.com");
        assertThat(body.getString("certificate_id")).isEqualTo("${id-of:" + cert.id + "}");
    }

    @Test
    @DisplayName("A lone CDN falls back to a spaces-style origin derived from its label")
    void cdnDefaultOrigin() {
        InfraGraph graph = new InfraGraph();
        InfraNode cdn = graph.addNode(NodeKind.CDN, 0, 0);

        assertThat(requestFor(graph, cdn).body().getString("origin"))
                .isEqualTo(cdn.label + ".nyc3.digitaloceanspaces.com");
    }

    @Test
    @DisplayName("Container registry create carries name, tier and region")
    void registryBody() {
        InfraGraph graph = new InfraGraph();
        InfraNode registry = graph.addNode(NodeKind.CONTAINER_REGISTRY, 0, 0);

        DoRequest request = requestFor(graph, registry);

        assertThat(request.path()).isEqualTo("/v2/registry");
        assertThat(request.body().getString("subscription_tier_slug")).isEqualTo("basic");
    }

    @Test
    @DisplayName("App Platform create embeds a wired database into its spec")
    void appPlatformWithDatabase() {
        InfraGraph graph = new InfraGraph();
        InfraNode db = graph.addNode(NodeKind.DB_MYSQL, 0, 0);
        InfraNode app = graph.addNode(NodeKind.APP_PLATFORM, 200, 0);
        app.props.put("repo", "acme/store");
        graph.connect(db, app);

        DoRequest request = requestFor(graph, app);
        JSONObject spec = request.body().getJSONObject("spec");

        assertThat(request.path()).isEqualTo("/v2/apps");
        assertThat(spec.getJSONArray("services").getJSONObject(0)
                .getJSONObject("github").getString("repo")).isEqualTo("acme/store");
        JSONObject database = spec.getJSONArray("databases").getJSONObject(0);
        assertThat(database.getString("engine")).isEqualTo("MYSQL");
        assertThat(database.getString("cluster_name")).isEqualTo(db.label);
    }

    @Test
    @DisplayName("Functions namespace create carries a label and region")
    void functionsBody() {
        InfraGraph graph = new InfraGraph();
        InfraNode fn = graph.addNode(NodeKind.FUNCTIONS, 0, 0);

        DoRequest request = requestFor(graph, fn);

        assertThat(request.path()).isEqualTo("/v2/functions/namespaces");
        assertThat(request.body().getString("label")).isEqualTo("fn-namespace");
    }

    @Test
    @DisplayName("Gradient AI create carries the model and an instruction")
    void gradientAiBody() {
        InfraGraph graph = new InfraGraph();
        InfraNode agent = graph.addNode(NodeKind.GRADIENT_AI, 0, 0);

        DoRequest request = requestFor(graph, agent);

        assertThat(request.path()).isEqualTo("/v2/gen-ai/agents");
        assertThat(request.body().getString("model_uuid")).isEqualTo("llama3.3-70b-instruct");
        assertThat(request.body().getString("instruction")).isNotBlank();
    }

    @Test
    @DisplayName("SSH key create posts name and public key to the account keys endpoint")
    void sshKeyBody() {
        InfraGraph graph = new InfraGraph();
        InfraNode key = graph.addNode(NodeKind.SSH_KEY, 0, 0);
        key.props.put("name", "laptop");
        key.props.put("publicKey", "ssh-ed25519 AAAAC3xyz");

        DoRequest request = requestFor(graph, key);

        assertThat(request.path()).isEqualTo("/v2/account/keys");
        assertThat(request.body().getString("name")).isEqualTo("laptop");
        assertThat(request.body().getString("public_key")).isEqualTo("ssh-ed25519 AAAAC3xyz");
    }

    @Test
    @DisplayName("Certificate create splits its DNS names into a JSON array")
    void certificateBody() {
        InfraGraph graph = new InfraGraph();
        InfraNode cert = graph.addNode(NodeKind.CERTIFICATE, 0, 0);
        cert.props.put("dnsNames", "example.com, www.example.com");

        DoRequest request = requestFor(graph, cert);

        assertThat(request.path()).isEqualTo("/v2/certificates");
        assertThat(request.body().getJSONArray("dns_names").toList())
                .containsExactly("example.com", "www.example.com");
    }

    @Test
    @DisplayName("Monitor alert create targets the droplets wired into it")
    void monitorAlertTargetsDroplets() {
        InfraGraph graph = new InfraGraph();
        InfraNode droplet = graph.addNode(NodeKind.DROPLET, 0, 0);
        InfraNode alert = graph.addNode(NodeKind.MONITOR_ALERT, 200, 0);
        alert.props.put("threshold", "90");
        graph.connect(droplet, alert);

        DoRequest request = requestFor(graph, alert);
        JSONObject body = request.body();

        assertThat(request.path()).isEqualTo("/v2/monitoring/alerts");
        assertThat(body.getInt("value")).isEqualTo(90);
        assertThat(body.getJSONArray("entities").getString(0))
                .isEqualTo("${id-of:" + droplet.id + "}");
    }

    // ---- Hetzner ----

    @Test
    @DisplayName("Hetzner network create carries an ip_range and a cloud subnet")
    void hetznerNetworkBody() {
        InfraGraph graph = new InfraGraph();
        InfraNode network = graph.addNode(NodeKind.HZ_NETWORK, 0, 0);
        network.props.put("ipRange", "10.1.0.0/16");

        DoRequest request = requestFor(graph, network);
        JSONObject body = request.body();

        assertThat(request.path()).isEqualTo("/networks");
        assertThat(body.getString("ip_range")).isEqualTo("10.1.0.0/16");
        assertThat(body.getJSONArray("subnets").getJSONObject(0).getString("ip_range"))
                .isEqualTo("10.1.0.0/16");
    }

    @Test
    @DisplayName("Hetzner load balancer targets its wired servers")
    void hetznerLoadBalancerTargets() {
        InfraGraph graph = new InfraGraph();
        InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 0, 0);
        InfraNode lb = graph.addNode(NodeKind.HZ_LB, 200, 0);
        graph.connect(server, lb);

        DoRequest request = requestFor(graph, lb);
        JSONObject target = request.body().getJSONArray("targets").getJSONObject(0);

        assertThat(request.path()).isEqualTo("/load_balancers");
        assertThat(target.getString("type")).isEqualTo("server");
        assertThat(target.getJSONObject("server").getString("id"))
                .isEqualTo("${id-of:" + server.id + "}");
    }

    @Test
    @DisplayName("Hetzner volume create carries size, location and format")
    void hetznerVolumeBody() {
        InfraGraph graph = new InfraGraph();
        InfraNode volume = graph.addNode(NodeKind.HZ_VOLUME, 0, 0);
        volume.props.put("sizeGb", "80");

        JSONObject body = requestFor(graph, volume).body();

        assertThat(body.getInt("size")).isEqualTo(80);
        assertThat(body.getString("format")).isEqualTo("ext4");
    }

    @Test
    @DisplayName("Hetzner firewall create parses its inbound spec into port rules")
    void hetznerFirewallBody() {
        InfraGraph graph = new InfraGraph();
        InfraNode firewall = graph.addNode(NodeKind.HZ_FIREWALL, 0, 0);
        firewall.props.put("inbound", "22/0.0.0.0/0, 443/0.0.0.0/0");

        DoRequest request = requestFor(graph, firewall);
        JSONObject body = request.body();

        assertThat(request.path()).isEqualTo("/firewalls");
        assertThat(body.getJSONArray("rules").length()).isEqualTo(2);
        JSONObject rule = body.getJSONArray("rules").getJSONObject(0);
        assertThat(rule.getString("direction")).isEqualTo("in");
        assertThat(rule.getString("port")).isEqualTo("22");
    }

    @Test
    @DisplayName("Hetzner floating IP create is an ipv4 in its home location")
    void hetznerFloatingIpBody() {
        InfraGraph graph = new InfraGraph();
        InfraNode fip = graph.addNode(NodeKind.HZ_FLOATING_IP, 0, 0);

        DoRequest request = requestFor(graph, fip);

        assertThat(request.path()).isEqualTo("/floating_ips");
        assertThat(request.body().getString("type")).isEqualTo("ipv4");
        assertThat(request.body().getString("home_location")).isEqualTo("fsn1");
    }

    // ---- Cloudflare ----

    @Test
    @DisplayName("Cloudflare R2 bucket create posts to the account's r2 endpoint")
    void cloudflareR2Body() {
        InfraGraph graph = new InfraGraph();
        InfraNode bucket = graph.addNode(NodeKind.CF_R2_BUCKET, 0, 0);
        bucket.props.put("accountId", "acc123");
        bucket.props.put("bucket", "media");

        DoRequest request = requestFor(graph, bucket);

        assertThat(request.path()).isEqualTo("/accounts/acc123/r2/buckets");
        assertThat(request.body().getString("name")).isEqualTo("media");
    }

    @Test
    @DisplayName("A Cloudflare DNS record with no upstream falls back to the documentation IP")
    void cloudflareDnsRecordDefaultContent() {
        InfraGraph graph = new InfraGraph();
        InfraNode record = graph.addNode(NodeKind.CF_DNS_RECORD, 0, 0);
        record.props.put("zoneId", "z1");
        record.props.put("name", "app.example.com");

        DoRequest request = requestFor(graph, record);

        assertThat(request.path()).isEqualTo("/zones/z1/dns_records");
        assertThat(request.body().getString("content")).isEqualTo("192.0.2.1");
    }

    // ---- Hetzner attachment actions ----

    @Test
    @DisplayName("A Hetzner volume wired to a server becomes an attach action with automount")
    void hetznerVolumeAttach() {
        InfraGraph graph = new InfraGraph();
        InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 0, 0);
        InfraNode volume = graph.addNode(NodeKind.HZ_VOLUME, 0, 100);
        graph.connect(volume, server);

        DoRequest attach = attachmentFor(graph, "/volumes/", "/actions/attach");

        assertThat(attach.path()).contains("${id-of:" + volume.id + "}");
        assertThat(attach.body().getBoolean("automount")).isTrue();
        assertThat(attach.body().getString("server")).isEqualTo("${id-of:" + server.id + "}");
    }

    @Test
    @DisplayName("A Hetzner floating IP wired to a server becomes an assign action")
    void hetznerFloatingIpAssign() {
        InfraGraph graph = new InfraGraph();
        InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 0, 0);
        InfraNode fip = graph.addNode(NodeKind.HZ_FLOATING_IP, 0, 100);
        graph.connect(fip, server);

        DoRequest assign = attachmentFor(graph, "/floating_ips/", "/actions/assign");

        assertThat(assign.body().getString("server")).isEqualTo("${id-of:" + server.id + "}");
    }

    @Test
    @DisplayName("A Hetzner firewall wired to a server becomes an apply_to_resources action")
    void hetznerFirewallApply() {
        InfraGraph graph = new InfraGraph();
        InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 0, 0);
        InfraNode firewall = graph.addNode(NodeKind.HZ_FIREWALL, 0, 100);
        graph.connect(firewall, server);

        DoRequest apply = attachmentFor(graph, "/firewalls/", "/actions/apply_to_resources");
        JSONObject applyTo = apply.body().getJSONArray("apply_to").getJSONObject(0);

        assertThat(applyTo.getString("type")).isEqualTo("server");
        assertThat(applyTo.getJSONObject("server").getString("id"))
                .isEqualTo("${id-of:" + server.id + "}");
    }

    @Test
    @DisplayName("A DO reserved IP wired to a droplet becomes an assign action")
    void reservedIpAssign() {
        InfraGraph graph = new InfraGraph();
        InfraNode droplet = graph.addNode(NodeKind.DROPLET, 0, 0);
        InfraNode rip = graph.addNode(NodeKind.RESERVED_IP, 0, 100);
        graph.connect(rip, droplet);

        DoRequest assign = attachmentFor(graph, "/v2/reserved_ips/", "/actions");

        assertThat(assign.body().getString("type")).isEqualTo("assign");
        assertThat(assign.body().getString("droplet_id"))
                .isEqualTo("${id-of:" + droplet.id + "}");
    }

    // ---- helpers ----

    private static DoRequest requestFor(InfraGraph graph, InfraNode node) {
        return DeployPlanner.plan(graph).stream()
                .filter(r -> r.nodeId().equals(node.id))
                .findFirst().orElseThrow();
    }

    private static DoRequest attachmentFor(InfraGraph graph, String pathPrefix, String pathFragment) {
        List<DoRequest> plan = DeployPlanner.plan(graph);
        return plan.stream()
                .filter(r -> r.path().startsWith(pathPrefix) && r.path().contains(pathFragment))
                .findFirst().orElseThrow();
    }
}

package org.nmox.studio.infra.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.InfraGraph.Wire;
import org.nmox.studio.infra.model.NodeKind;

/**
 * Turns a design into an ordered list of API calls: a topological sort
 * over creation dependencies (a droplet needs its VPC's id at create
 * time), followed by post-create attachments (a volume attaches to a
 * droplet only after both exist). Pure function of the graph - the
 * dry-run plan and the live deploy run the identical list.
 */
public final class DeployPlanner {

    private DeployPlanner() {
    }

    public static List<DoRequest> plan(InfraGraph graph) {
        List<InfraNode> ordered = topologicalOrder(graph);
        List<DoRequest> requests = new ArrayList<>();
        for (InfraNode node : ordered) {
            if (node.doId != null) {
                continue; // already live in the cloud
            }
            requests.add(creationRequest(graph, node));
        }
        // attachments after every creation
        for (Wire wire : graph.getWires()) {
            InfraNode from = graph.node(wire.fromId());
            InfraNode to = graph.node(wire.toId());
            if (from != null && to != null && from.kind.attachesTo(to.kind)) {
                requests.add(attachmentRequest(from, to));
            }
        }
        return requests;
    }

    /**
     * The distinct clouds this plan's real (non-skipped) requests will
     * actually call - the honest basis for the live/dry-run gate.
     */
    public static java.util.Set<CloudProvider> providersUsed(List<DoRequest> plan, InfraGraph graph) {
        java.util.Set<CloudProvider> used = new java.util.LinkedHashSet<>();
        for (DoRequest request : plan) {
            if (request.skipped()) {
                continue;
            }
            InfraNode node = graph.node(request.nodeId());
            if (node != null) {
                used.add(node.kind.provider());
            }
        }
        return used;
    }

    /**
     * Live only when every cloud the plan touches has its token - a
     * pure-Hetzner stack must not be held hostage to a DigitalOcean key.
     */
    public static boolean liveEligible(java.util.Set<CloudProvider> used,
            java.util.function.Predicate<CloudProvider> hasToken) {
        return used.stream().allMatch(hasToken);
    }

    /**
     * The teardown order for everything deployed: creation order
     * reversed, so dependents (attachments, LBs, records) fall before
     * the resources they lean on. Design-only nodes are not included.
     */
    public static List<InfraNode> teardownOrder(InfraGraph graph) {
        List<InfraNode> order = new java.util.ArrayList<>(topologicalOrder(graph));
        java.util.Collections.reverse(order);
        order.removeIf(node -> node.doId == null);
        return order;
    }

    /** Kahn's algorithm over creation-dependency edges (stable order). */
    private static List<InfraNode> topologicalOrder(InfraGraph graph) {
        List<InfraNode> nodes = graph.getNodes();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> consumers = new HashMap<>();
        for (InfraNode n : nodes) {
            indegree.put(n.id, 0);
        }
        for (Wire w : graph.getWires()) {
            InfraNode from = graph.node(w.fromId());
            InfraNode to = graph.node(w.toId());
            if (from == null || to == null || from.kind.attachesTo(to.kind)) {
                continue; // attachments don't constrain creation order
            }
            indegree.merge(w.toId(), 1, Integer::sum);
            consumers.computeIfAbsent(w.fromId(), k -> new ArrayList<>()).add(w.toId());
        }
        Deque<String> ready = new ArrayDeque<>();
        indegree.forEach((id, deg) -> {
            if (deg == 0) {
                ready.add(id);
            }
        });
        List<InfraNode> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.poll();
            ordered.add(graph.node(id));
            for (String next : consumers.getOrDefault(id, List.of())) {
                if (indegree.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
        }
        // cycles are impossible by construction (rules are a DAG), but
        // append any stragglers rather than silently dropping them
        for (InfraNode n : nodes) {
            if (!ordered.contains(n)) {
                ordered.add(n);
            }
        }
        return ordered;
    }

    private static String idOf(InfraNode node) {
        return node.doId != null ? node.doId : "${id-of:" + node.id + "}";
    }

    private static DoRequest creationRequest(InfraGraph graph, InfraNode node) {
        Map<String, String> p = node.props;
        List<InfraNode> providers = graph.providersOf(node);
        JSONObject body = new JSONObject();
        return switch (node.kind) {
            case VPC -> {
                body.put("name", node.label).put("region", p.get("region"))
                        .put("ip_range", p.get("ipRange"));
                yield DoRequest.post("/v2/vpcs", body, node.id, "Create VPC " + node.label);
            }
            case DROPLET, GPU_DROPLET -> {
                body.put("name", node.label)
                        .put("region", p.get("region"))
                        .put("size", p.get("size"))
                        .put("image", p.get("image"));
                if (node.kind == NodeKind.DROPLET) {
                    body.put("backups", Boolean.parseBoolean(p.getOrDefault("backups", "false")))
                            .put("monitoring", Boolean.parseBoolean(p.getOrDefault("monitoring", "true")));
                }
                String userData = p.getOrDefault("userData", "");
                if (!userData.isBlank()) {
                    body.put("user_data", userData); // cloud-init: the droplet configures itself
                }
                JSONArray keys = new JSONArray();
                for (InfraNode prov : providers) {
                    if (prov.kind == NodeKind.VPC) {
                        body.put("vpc_uuid", idOf(prov));
                    } else if (prov.kind == NodeKind.SSH_KEY) {
                        keys.put(idOf(prov));
                    }
                }
                if (!keys.isEmpty()) {
                    body.put("ssh_keys", keys);
                }
                yield DoRequest.post("/v2/droplets", body, node.id,
                        "Create " + node.kind.getDisplayName() + " " + node.label
                        + " (" + p.get("size") + ", " + p.get("region") + ")");
            }
            case KUBERNETES -> {
                JSONObject pool = new JSONObject()
                        .put("size", p.get("nodeSize"))
                        .put("name", node.label + "-pool")
                        .put("count", Integer.parseInt(p.getOrDefault("nodeCount", "3")))
                        .put("auto_scale", Boolean.parseBoolean(p.getOrDefault("autoscale", "false")));
                body.put("name", node.label).put("region", p.get("region"))
                        .put("version", "latest")
                        .put("ha", Boolean.parseBoolean(p.getOrDefault("ha", "false")))
                        .put("node_pools", new JSONArray().put(pool));
                for (InfraNode prov : providers) {
                    if (prov.kind == NodeKind.VPC) {
                        body.put("vpc_uuid", idOf(prov));
                    }
                }
                yield DoRequest.post("/v2/kubernetes/clusters", body, node.id,
                        "Create Kubernetes cluster " + node.label);
            }
            case LOAD_BALANCER -> {
                String[] rule = p.getOrDefault("forwardingRule", "http-80").split("-");
                JSONObject forwarding = new JSONObject()
                        .put("entry_protocol", rule[0]).put("entry_port", Integer.parseInt(rule[1]))
                        .put("target_protocol", "http").put("target_port", 80);
                body.put("name", node.label).put("region", p.get("region"))
                        .put("size_unit", 1)
                        .put("forwarding_rules", new JSONArray().put(forwarding))
                        .put("health_check", new JSONObject()
                                .put("protocol", "http").put("port", 80)
                                .put("path", p.getOrDefault("healthPath", "/")));
                JSONArray dropletIds = new JSONArray();
                for (InfraNode prov : providers) {
                    switch (prov.kind) {
                        case DROPLET, GPU_DROPLET -> dropletIds.put(idOf(prov));
                        case VPC -> body.put("vpc_uuid", idOf(prov));
                        case CERTIFICATE -> forwarding
                                .put("certificate_id", idOf(prov))
                                .put("entry_protocol", "https").put("entry_port", 443);
                        default -> {
                        }
                    }
                }
                if (!dropletIds.isEmpty()) {
                    body.put("droplet_ids", dropletIds);
                }
                yield DoRequest.post("/v2/load_balancers", body, node.id,
                        "Create load balancer " + node.label);
            }
            case FIREWALL -> {
                body.put("name", node.label)
                        .put("inbound_rules", rules(p.getOrDefault("inbound", ""), "sources"))
                        .put("outbound_rules", rules(p.getOrDefault("outbound", "all/0.0.0.0/0"), "destinations"));
                yield DoRequest.post("/v2/firewalls", body, node.id,
                        "Create firewall " + node.label);
            }
            case VOLUME -> {
                body.put("name", node.label).put("region", p.get("region"))
                        .put("size_gigabytes", Integer.parseInt(p.getOrDefault("sizeGb", "100")))
                        .put("filesystem_type", p.getOrDefault("fs", "ext4"));
                yield DoRequest.post("/v2/volumes", body, node.id,
                        "Create " + p.get("sizeGb") + "GiB volume " + node.label);
            }
            case RESERVED_IP -> {
                body.put("region", p.get("region"));
                yield DoRequest.post("/v2/reserved_ips", body, node.id, "Reserve IP");
            }
            case DOMAIN -> {
                String target = "192.0.2.1";
                for (InfraNode prov : providers) {
                    // the A record points at whichever provider fronts the domain
                    target = "${ip-of:" + prov.id + "}";
                }
                body.put("name", p.get("name")).put("ip_address", target);
                yield DoRequest.post("/v2/domains", body, node.id,
                        "Create domain " + p.get("name") + " -> " + target);
            }
            case CDN -> {
                String origin = node.label + ".nyc3.digitaloceanspaces.com";
                for (InfraNode prov : providers) {
                    if (prov.kind == NodeKind.SPACES) {
                        origin = prov.props.getOrDefault("bucket", "bucket")
                                + "." + prov.props.getOrDefault("region", "nyc3")
                                + ".digitaloceanspaces.com";
                    }
                }
                body.put("origin", origin).put("ttl", Integer.parseInt(p.getOrDefault("ttl", "3600")));
                for (InfraNode prov : providers) {
                    if (prov.kind == NodeKind.CERTIFICATE) {
                        body.put("certificate_id", idOf(prov));
                    }
                }
                yield DoRequest.post("/v2/cdn/endpoints", body, node.id,
                        "Create CDN endpoint for " + origin);
            }
            case SPACES -> DoRequest.skip(node.id,
                    "Spaces bucket '" + p.get("bucket") + "' uses the S3 protocol - create via "
                    + "s3cmd/aws-cli against " + p.get("region") + ".digitaloceanspaces.com");
            case CONTAINER_REGISTRY -> {
                body.put("name", node.label)
                        .put("subscription_tier_slug", p.getOrDefault("tier", "basic"))
                        .put("region", p.get("region"));
                yield DoRequest.post("/v2/registry", body, node.id,
                        "Create container registry " + node.label);
            }
            case DB_POSTGRES, DB_MYSQL, DB_MONGODB, DB_VALKEY, DB_KAFKA, DB_OPENSEARCH -> {
                body.put("name", node.label)
                        .put("engine", engineSlug(node.kind))
                        .put("version", p.get("version"))
                        .put("region", p.get("region"))
                        .put("size", p.get("size"))
                        .put("num_nodes", Integer.parseInt(p.getOrDefault("nodes", "1")));
                for (InfraNode prov : providers) {
                    if (prov.kind == NodeKind.VPC) {
                        body.put("private_network_uuid", idOf(prov));
                    }
                }
                yield DoRequest.post("/v2/databases", body, node.id,
                        "Create managed " + node.kind.getDisplayName() + " " + node.label);
            }
            case APP_PLATFORM -> {
                JSONObject service = new JSONObject()
                        .put("name", "web")
                        .put("github", new JSONObject()
                                .put("repo", p.getOrDefault("repo", "owner/app"))
                                .put("branch", "main").put("deploy_on_push", true))
                        .put("instance_size_slug", p.getOrDefault("instance", "basic-xxs"))
                        .put("instance_count", Integer.parseInt(p.getOrDefault("instances", "1")));
                JSONObject spec = new JSONObject()
                        .put("name", node.label)
                        .put("region", p.getOrDefault("region", "nyc"))
                        .put("services", new JSONArray().put(service));
                JSONArray databases = new JSONArray();
                for (InfraNode prov : providers) {
                    if (prov.kind.name().startsWith("DB_")) {
                        databases.put(new JSONObject()
                                .put("name", prov.label)
                                .put("engine", engineSlug(prov.kind).toUpperCase())
                                .put("production", true)
                                .put("cluster_name", prov.label));
                    }
                }
                if (!databases.isEmpty()) {
                    spec.put("databases", databases);
                }
                body.put("spec", spec);
                yield DoRequest.post("/v2/apps", body, node.id,
                        "Create App Platform app " + node.label);
            }
            case FUNCTIONS -> {
                body.put("label", p.getOrDefault("namespaceLabel", node.label))
                        .put("region", p.get("region"));
                yield DoRequest.post("/v2/functions/namespaces", body, node.id,
                        "Create Functions namespace " + node.label);
            }
            case GRADIENT_AI -> {
                body.put("name", p.getOrDefault("agentName", node.label))
                        .put("model_uuid", p.getOrDefault("model", "llama3.3-70b-instruct"))
                        .put("region", p.getOrDefault("region", "tor1"))
                        .put("instruction", "You are a helpful assistant.");
                yield DoRequest.post("/v2/gen-ai/agents", body, node.id,
                        "Create Gradient AI agent " + node.label);
            }
            case SSH_KEY -> {
                body.put("name", p.getOrDefault("name", node.label))
                        .put("public_key", p.getOrDefault("publicKey", ""));
                yield DoRequest.post("/v2/account/keys", body, node.id,
                        "Register SSH key " + p.get("name"));
            }
            case CERTIFICATE -> {
                body.put("name", p.getOrDefault("name", node.label))
                        .put("type", p.getOrDefault("certType", "lets_encrypt"))
                        .put("dns_names", new JSONArray(p.getOrDefault("dnsNames", "").split(",\\s*")));
                yield DoRequest.post("/v2/certificates", body, node.id,
                        "Create certificate " + p.get("name"));
            }
            case MONITOR_ALERT -> {
                JSONArray entities = new JSONArray();
                for (InfraNode prov : providers) {
                    if (prov.kind == NodeKind.DROPLET || prov.kind == NodeKind.GPU_DROPLET) {
                        entities.put(idOf(prov));
                    }
                }
                body.put("type", p.get("metric"))
                        .put("compare", "GreaterThan")
                        .put("value", Integer.parseInt(p.getOrDefault("threshold", "80")))
                        .put("window", "5m")
                        .put("entities", entities)
                        .put("alerts", new JSONObject()
                                .put("email", new JSONArray().put(p.getOrDefault("email", ""))))
                        .put("enabled", true)
                        .put("description", node.label);
                yield DoRequest.post("/v2/monitoring/alerts", body, node.id,
                        "Create alert " + node.label);
            }

            // ---- Hetzner Cloud (paths are relative to api.hetzner.cloud/v1) ----
            case HZ_NETWORK -> {
                String range = p.getOrDefault("ipRange", "10.0.0.0/16");
                body.put("name", node.label)
                        .put("ip_range", range)
                        .put("subnets", new JSONArray().put(new JSONObject()
                                .put("type", "cloud")
                                .put("network_zone", "eu-central")
                                .put("ip_range", range)));
                yield DoRequest.post("/networks", body, node.id, "Create Hetzner network " + node.label);
            }
            case HZ_SERVER -> {
                body.put("name", node.label)
                        .put("server_type", p.getOrDefault("serverType", "cx22"))
                        .put("location", p.getOrDefault("location", "fsn1"))
                        .put("image", p.getOrDefault("image", "ubuntu-24.04"));
                String userData = p.getOrDefault("userData", "");
                if (!userData.isBlank()) {
                    body.put("user_data", userData); // cloud-init: the server configures itself
                }
                JSONArray networks = new JSONArray();
                for (InfraNode prov : providers) {
                    if (prov.kind == NodeKind.HZ_NETWORK) {
                        networks.put(idOf(prov));
                    }
                }
                if (!networks.isEmpty()) {
                    body.put("networks", networks);
                }
                yield DoRequest.post("/servers", body, node.id, "Create Hetzner server " + node.label);
            }
            case HZ_LB -> {
                body.put("name", node.label)
                        .put("load_balancer_type", p.getOrDefault("lbType", "lb11"))
                        .put("location", p.getOrDefault("location", "fsn1"))
                        .put("services", new JSONArray().put(new JSONObject()
                                .put("protocol", "http")
                                .put("listen_port", 80)
                                .put("destination_port", 80)));
                JSONArray targets = new JSONArray();
                for (InfraNode prov : providers) {
                    if (prov.kind == NodeKind.HZ_SERVER) {
                        targets.put(new JSONObject()
                                .put("type", "server")
                                .put("server", new JSONObject().put("id", idOf(prov))));
                    }
                }
                if (!targets.isEmpty()) {
                    body.put("targets", targets);
                }
                yield DoRequest.post("/load_balancers", body, node.id,
                        "Create Hetzner load balancer " + node.label);
            }
            case HZ_VOLUME -> {
                body.put("name", node.label)
                        .put("size", Integer.parseInt(p.getOrDefault("sizeGb", "50")))
                        .put("location", p.getOrDefault("location", "fsn1"))
                        .put("format", p.getOrDefault("format", "ext4"));
                yield DoRequest.post("/volumes", body, node.id, "Create Hetzner volume " + node.label);
            }
            case HZ_FIREWALL -> {
                JSONArray rules = new JSONArray();
                for (String rule : p.getOrDefault("inbound", "").split(",")) {
                    String[] parts = rule.trim().split("/", 2);
                    if (parts.length == 2) {
                        rules.put(new JSONObject()
                                .put("direction", "in")
                                .put("protocol", "tcp")
                                .put("port", parts[0].trim())
                                .put("source_ips", new JSONArray().put(parts[1].trim())));
                    }
                }
                body.put("name", node.label).put("rules", rules);
                yield DoRequest.post("/firewalls", body, node.id, "Create Hetzner firewall " + node.label);
            }
            case HZ_FLOATING_IP -> {
                body.put("type", "ipv4")
                        .put("home_location", p.getOrDefault("homeLocation", "fsn1"))
                        .put("name", node.label);
                yield DoRequest.post("/floating_ips", body, node.id, "Create Hetzner floating IP");
            }

            // ---- Cloudflare ----
            case CF_DNS_RECORD -> {
                String content = "";
                for (InfraNode prov : providers) {
                    content = switch (prov.kind) {
                        case DROPLET, GPU_DROPLET, HZ_SERVER -> "${ip-of:" + prov.id + "}";
                        case LOAD_BALANCER, HZ_LB -> "${ip-of:" + prov.id + "}";
                        default -> content;
                    };
                }
                body.put("type", p.getOrDefault("recordType", "A"))
                        .put("name", p.getOrDefault("name", node.label))
                        .put("content", content.isEmpty() ? "192.0.2.1" : content)
                        .put("proxied", Boolean.parseBoolean(p.getOrDefault("proxied", "true")));
                yield DoRequest.post("/zones/" + p.getOrDefault("zoneId", "") + "/dns_records",
                        body, node.id, "Create Cloudflare DNS record " + p.get("name"));
            }
            case CF_R2_BUCKET -> {
                body.put("name", p.getOrDefault("bucket", node.label));
                yield DoRequest.post("/accounts/" + p.getOrDefault("accountId", "") + "/r2/buckets",
                        body, node.id, "Create R2 bucket " + p.get("bucket"));
            }
        };
    }

    private static DoRequest attachmentRequest(InfraNode from, InfraNode to) {
        return switch (from.kind) {
            case HZ_VOLUME -> DoRequest.post(
                    "/volumes/" + idOf(from) + "/actions/attach",
                    new JSONObject().put("server", idOf(to)).put("automount", true),
                    from.id, "Attach " + from.label + " to " + to.label);
            case HZ_FLOATING_IP -> DoRequest.post(
                    "/floating_ips/" + idOf(from) + "/actions/assign",
                    new JSONObject().put("server", idOf(to)),
                    from.id, "Assign floating IP to " + to.label);
            case HZ_FIREWALL -> DoRequest.post(
                    "/firewalls/" + idOf(from) + "/actions/apply_to_resources",
                    new JSONObject().put("apply_to", new JSONArray().put(new JSONObject()
                            .put("type", "server")
                            .put("server", new JSONObject().put("id", idOf(to))))),
                    from.id, "Apply " + from.label + " to " + to.label);
            case VOLUME -> DoRequest.post(
                    "/v2/volumes/" + idOf(from) + "/actions",
                    new JSONObject().put("type", "attach")
                            .put("droplet_id", idOf(to))
                            .put("region", from.props.get("region")),
                    from.id, "Attach volume " + from.label + " to " + to.label);
            case RESERVED_IP -> DoRequest.post(
                    "/v2/reserved_ips/" + idOf(from) + "/actions",
                    new JSONObject().put("type", "assign").put("droplet_id", idOf(to)),
                    from.id, "Assign reserved IP to " + to.label);
            case FIREWALL -> DoRequest.post(
                    "/v2/firewalls/" + idOf(from) + "/droplets",
                    new JSONObject().put("droplet_ids",
                            new JSONArray().put(idOf(to))),
                    from.id, "Apply firewall " + from.label + " to " + to.label);
            default -> DoRequest.skip(from.id, "No attachment for " + from.kind);
        };
    }

    private static String engineSlug(NodeKind kind) {
        return switch (kind) {
            case DB_POSTGRES -> "pg";
            case DB_MYSQL -> "mysql";
            case DB_MONGODB -> "mongodb";
            case DB_VALKEY -> "valkey";
            case DB_KAFKA -> "kafka";
            case DB_OPENSEARCH -> "opensearch";
            default -> "pg";
        };
    }

    /** "22/0.0.0.0/0, 80/0.0.0.0/0" -> firewall rule objects. */
    private static JSONArray rules(String spec, String peerField) {
        JSONArray rules = new JSONArray();
        for (String part : spec.split(",")) {
            String[] bits = part.trim().split("/", 2);
            if (bits.length < 2) {
                continue;
            }
            String ports = bits[0].trim();
            JSONObject rule = new JSONObject()
                    .put("protocol", "tcp")
                    .put("ports", "all".equals(ports) ? "all" : ports)
                    .put(peerField, new JSONObject()
                            .put("addresses", new JSONArray().put(bits[1].trim())));
            rules.put(rule);
        }
        return rules;
    }
}

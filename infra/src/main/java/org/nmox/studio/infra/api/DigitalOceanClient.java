package org.nmox.studio.infra.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;
import org.openide.util.NbPreferences;

/**
 * The live wire to DigitalOcean's v2 REST API. Executes deploy plans
 * (resolving cross-step id/ip placeholders as resources come up),
 * imports existing resources, and destroys per node. Without a token
 * everything stays in dry-run; with one, DEPLOY is real.
 */
public final class DigitalOceanClient {

    public static final String API = "https://api.digitalocean.com";
    private static final String PREF_TOKEN = "doToken";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(id|ip)-of:([^}]+)}");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // ---- token ----

    public static String token() {
        String env = System.getenv("DIGITALOCEAN_TOKEN");
        if (env != null && !env.isBlank()) {
            return env;
        }
        String pref = NbPreferences.forModule(DigitalOceanClient.class).get(PREF_TOKEN, "");
        return pref.isBlank() ? null : pref;
    }

    public static void storeToken(String token) {
        NbPreferences.forModule(DigitalOceanClient.class).put(PREF_TOKEN, token == null ? "" : token);
    }

    public static boolean hasToken() {
        return token() != null;
    }

    // ---- plan execution ----

    /**
     * Runs a plan against the API. Per-node ids land back on the graph
     * nodes; progress flows through the callback (worker thread).
     *
     * @param onStep (node, message) per state change
     * @return true when every step succeeded
     */
    public boolean execute(List<DoRequest> plan, InfraGraph graph,
            BiConsumer<InfraNode, String> onStep) {
        Map<String, String> ids = new ConcurrentHashMap<>();
        Map<String, String> ips = new ConcurrentHashMap<>();
        for (InfraNode node : graph.getNodes()) {
            if (node.doId != null) {
                ids.put(node.id, node.doId);
            }
        }
        for (DoRequest step : plan) {
            InfraNode node = graph.node(step.nodeId());
            if (step.skipped()) {
                onStep.accept(node, "skipped: " + step.description());
                continue;
            }
            onStep.accept(node, "creating…");
            try {
                String bodyText = step.body() == null ? "" : step.body().toString();
                bodyText = resolvePlaceholders(bodyText, graph, ids, ips, onStep, node);
                String path = resolvePlaceholders(step.path(), graph, ids, ips, onStep, node);
                CloudProvider provider = node != null ? node.kind.provider() : CloudProvider.DIGITALOCEAN;
                JSONObject response = send(provider, step.method(), path, bodyText);
                String doId = extractId(node == null ? null : node.kind, response);
                if (node != null && doId != null && node.doId == null) {
                    node.doId = doId;
                    ids.put(node.id, doId);
                }
                // honesty over green lights: a created resource whose id we
                // could not parse cannot be synced or destroyed later
                onStep.accept(node, node != null && node.doId == null
                        ? "created (id not parsed — destroy/sync unavailable)"
                        : "created");
            } catch (Exception ex) {
                onStep.accept(node, "FAILED: " + compact(ex.getMessage()));
                return false;
            }
        }
        return true;
    }

    private String resolvePlaceholders(String text, InfraGraph graph, Map<String, String> ids,
            Map<String, String> ips, BiConsumer<InfraNode, String> onStep,
            InfraNode forNode) throws IOException, InterruptedException {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String want = m.group(1);
            String nodeId = m.group(2);
            String value;
            if ("id".equals(want)) {
                value = ids.get(nodeId);
                if (value == null) {
                    throw new IOException("dependency " + nodeId + " has no id yet");
                }
            } else {
                value = ips.computeIfAbsent(nodeId, k -> null);
                if (value == null) {
                    onStep.accept(forNode, "waiting for IP of " + nodeId + "…");
                    InfraNode source = graph.node(nodeId);
                    value = source != null && source.kind.provider() == CloudProvider.HETZNER
                            ? waitForHetznerIp(ids.get(nodeId))
                            : waitForDropletIp(ids.get(nodeId));
                    ips.put(nodeId, value);
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Polls a droplet until it has a public IPv4 (max ~90s). */
    private String waitForDropletIp(String dropletId) throws IOException, InterruptedException {
        for (int i = 0; i < 30; i++) {
            JSONObject response = send("GET", "/v2/droplets/" + dropletId, "");
            JSONArray v4 = response.getJSONObject("droplet")
                    .getJSONObject("networks").optJSONArray("v4");
            if (v4 != null) {
                for (int j = 0; j < v4.length(); j++) {
                    JSONObject net = v4.getJSONObject(j);
                    if ("public".equals(net.optString("type"))) {
                        return net.getString("ip_address");
                    }
                }
            }
            Thread.sleep(3000);
        }
        throw new IOException("droplet " + dropletId + " got no public IP in time");
    }

    /** Hetzner servers report their IPv4 immediately; poll briefly anyway. */
    private String waitForHetznerIp(String serverId) throws IOException, InterruptedException {
        for (int i = 0; i < 10; i++) {
            JSONObject response = send(CloudProvider.HETZNER, "GET", "/servers/" + serverId, "");
            String ip = response.getJSONObject("server")
                    .getJSONObject("public_net").getJSONObject("ipv4").optString("ip", "");
            if (!ip.isEmpty()) {
                return ip;
            }
            Thread.sleep(2000);
        }
        throw new IOException("Hetzner server " + serverId + " got no public IP in time");
    }

    // ---- raw API ----

    private JSONObject send(String method, String path, String body)
            throws IOException, InterruptedException {
        return send(CloudProvider.DIGITALOCEAN, method, path, body);
    }

    private JSONObject send(CloudProvider provider, String method, String path, String body)
            throws IOException, InterruptedException {
        String tok = provider.token();
        if (tok == null) {
            throw new IOException("no " + provider.displayName() + " API token configured");
        }
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(provider.apiBase() + path))
                .header("Authorization", "Bearer " + tok)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60));
        switch (method) {
            case "POST" -> rb.POST(HttpRequest.BodyPublishers.ofString(body));
            case "DELETE" -> rb.DELETE();
            default -> rb.GET();
        }
        HttpResponse<String> response = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + compact(response.body()));
        }
        String text = response.body();
        return text == null || text.isBlank() ? new JSONObject() : new JSONObject(text);
    }

    /** Pulls the new resource's id out of a creation response, per kind. */
    static String extractId(NodeKind kind, JSONObject response) {
        if (kind == null) {
            return null;
        }
        try {
            return switch (kind) {
                case DROPLET, GPU_DROPLET -> String.valueOf(response.getJSONObject("droplet").getLong("id"));
                case VPC -> response.getJSONObject("vpc").getString("id");
                case LOAD_BALANCER -> response.getJSONObject("load_balancer").getString("id");
                case FIREWALL -> response.getJSONObject("firewall").getString("id");
                case VOLUME -> response.getJSONObject("volume").getString("id");
                case RESERVED_IP -> response.getJSONObject("reserved_ip").getString("ip");
                case DOMAIN -> response.getJSONObject("domain").getString("name");
                case CDN -> response.getJSONObject("endpoint").getString("id");
                case KUBERNETES -> response.getJSONObject("kubernetes_cluster").getString("id");
                case APP_PLATFORM -> response.getJSONObject("app").getString("id");
                case FUNCTIONS -> response.getJSONObject("namespace").getString("namespace");
                case CONTAINER_REGISTRY -> response.getJSONObject("registry").getString("name");
                case SSH_KEY -> String.valueOf(response.getJSONObject("ssh_key").getLong("id"));
                case CERTIFICATE -> response.getJSONObject("certificate").getString("id");
                case MONITOR_ALERT -> response.getJSONObject("policy").getString("uuid");
                case GRADIENT_AI -> response.getJSONObject("agent").optString("uuid", null);
                case DB_POSTGRES, DB_MYSQL, DB_MONGODB, DB_VALKEY, DB_KAFKA, DB_OPENSEARCH ->
                    response.getJSONObject("database").getString("id");
                case HZ_SERVER -> String.valueOf(response.getJSONObject("server").getLong("id"));
                case HZ_NETWORK -> String.valueOf(response.getJSONObject("network").getLong("id"));
                case HZ_LB -> String.valueOf(response.getJSONObject("load_balancer").getLong("id"));
                case HZ_VOLUME -> String.valueOf(response.getJSONObject("volume").getLong("id"));
                case HZ_FIREWALL -> String.valueOf(response.getJSONObject("firewall").getLong("id"));
                case HZ_FLOATING_IP -> String.valueOf(response.getJSONObject("floating_ip").getLong("id"));
                case CF_DNS_RECORD, CF_R2_BUCKET -> response.getJSONObject("result").optString("id", null);
                default -> null;
            };
        } catch (RuntimeException ex) {
            // unexpected response shape: without an id the node cannot be
            // synced or destroyed later, so make the parse failure visible
            java.util.logging.Logger.getLogger(DigitalOceanClient.class.getName())
                    .log(java.util.logging.Level.WARNING,
                            "Could not extract cloud id from response", ex);
            return null;
        }
    }

    // ---- sync & destroy ----

    /** Imports live resources as nodes laid out in a grid, marked live. */
    public int sync(InfraGraph graph) throws IOException, InterruptedException {
        record Source(String path, String listKey, NodeKind kind, String idKey, String nameKey) {
        }
        List<Source> sources = List.of(
                new Source("/v2/vpcs", "vpcs", NodeKind.VPC, "id", "name"),
                new Source("/v2/droplets", "droplets", NodeKind.DROPLET, "id", "name"),
                new Source("/v2/load_balancers", "load_balancers", NodeKind.LOAD_BALANCER, "id", "name"),
                new Source("/v2/volumes", "volumes", NodeKind.VOLUME, "id", "name"),
                new Source("/v2/domains", "domains", NodeKind.DOMAIN, "name", "name"),
                new Source("/v2/databases", "databases", NodeKind.DB_POSTGRES, "id", "name"),
                new Source("/v2/kubernetes/clusters", "kubernetes_clusters", NodeKind.KUBERNETES, "id", "name"),
                new Source("/v2/apps", "apps", NodeKind.APP_PLATFORM, "id", "spec"),
                new Source("/v2/firewalls", "firewalls", NodeKind.FIREWALL, "id", "name"),
                new Source("/v2/reserved_ips", "reserved_ips", NodeKind.RESERVED_IP, "ip", "ip"),
                new Source("/v2/certificates", "certificates", NodeKind.CERTIFICATE, "id", "name"),
                new Source("/v2/account/keys", "ssh_keys", NodeKind.SSH_KEY, "id", "name"),
                new Source("/v2/functions/namespaces", "namespaces", NodeKind.FUNCTIONS, "namespace", "label"));
        int imported = 0;
        int x = 80, y = 60;
        for (Source source : sources) {
            JSONObject response = send("GET", source.path() + "?per_page=100", "");
            JSONArray items = response.optJSONArray(source.listKey());
            if (items == null) {
                continue;
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String doId = String.valueOf(item.get(source.idKey()));
                boolean known = graph.getNodes().stream().anyMatch(n -> doId.equals(n.doId));
                if (known) {
                    continue;
                }
                InfraNode node = graph.addNode(source.kind(), x, y);
                if (source.kind() == NodeKind.APP_PLATFORM) {
                    // app names live one level down, in the spec
                    JSONObject spec = item.optJSONObject("spec");
                    node.label = spec != null ? spec.optString("name", node.label) : node.label;
                } else {
                    node.label = item.optString(source.nameKey(), node.label);
                }
                node.doId = doId;
                graph.setStatus(node, "live");
                imported++;
                x += 190;
                if (x > 900) {
                    x = 80;
                    y += 90;
                }
            }
            if (imported > 0) {
                x = 80;
                y += 90;
            }
        }
        return imported;
    }

    /**
     * The REST path addressing a node's live resource - shared by
     * destroy and drift checks; null when the provider offers no
     * per-resource endpoint (Cloudflare records, Hetzner kinds).
     */
    static String resourcePath(org.nmox.studio.infra.model.NodeKind kind, String id) {
        return switch (kind) {
            case DROPLET, GPU_DROPLET -> "/v2/droplets/" + id;
            case VPC -> "/v2/vpcs/" + id;
            case LOAD_BALANCER -> "/v2/load_balancers/" + id;
            case FIREWALL -> "/v2/firewalls/" + id;
            case VOLUME -> "/v2/volumes/" + id;
            case RESERVED_IP -> "/v2/reserved_ips/" + id;
            case DOMAIN -> "/v2/domains/" + id;
            case CDN -> "/v2/cdn/endpoints/" + id;
            case KUBERNETES -> "/v2/kubernetes/clusters/" + id;
            case APP_PLATFORM -> "/v2/apps/" + id;
            case FUNCTIONS -> "/v2/functions/namespaces/" + id;
            case CONTAINER_REGISTRY -> "/v2/registry";
            case SSH_KEY -> "/v2/account/keys/" + id;
            case CERTIFICATE -> "/v2/certificates/" + id;
            case MONITOR_ALERT -> "/v2/monitoring/alerts/" + id;
            case GRADIENT_AI -> "/v2/gen-ai/agents/" + id;
            case DB_POSTGRES, DB_MYSQL, DB_MONGODB, DB_VALKEY, DB_KAFKA, DB_OPENSEARCH ->
                "/v2/databases/" + id;
            default -> null;
        };
    }

    /**
     * Drift check: asks the cloud whether each deployed node still
     * exists. A resource deleted behind the designer's back stops
     * claiming "live" - it reports drifted instead. Kinds without a
     * read endpoint are labeled honestly, never guessed.
     */
    public void refreshDrift(InfraGraph graph,
            java.util.function.BiConsumer<InfraNode, String> onStatus)
            throws IOException, InterruptedException {
        for (InfraNode node : graph.getNodes()) {
            if (node.doId == null) {
                continue;
            }
            String path = resourcePath(node.kind, node.doId);
            if (path == null) {
                onStatus.accept(node, "unverifiable (no read API)");
                continue;
            }
            try {
                send("GET", path, "");
                onStatus.accept(node, "live");
            } catch (IOException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                if (msg.contains("404")) {
                    node.doId = null; // the cloud is the truth: it is gone
                    onStatus.accept(node, "drifted: deleted in cloud");
                } else {
                    onStatus.accept(node, "check failed: " + (msg.length() > 60 ? msg.substring(0, 60) : msg));
                }
            }
        }
    }

    /**
     * Tears down the given nodes in the order handed in (callers pass
     * DeployPlanner.teardownOrder - reverse dependency order). Keeps
     * going past individual failures so one stuck resource does not
     * strand the rest of the bill; returns the failure count.
     */
    public int destroyAll(java.util.List<InfraNode> nodes,
            java.util.function.BiConsumer<InfraNode, String> onStep) {
        int failures = 0;
        for (InfraNode node : nodes) {
            try {
                onStep.accept(node, "destroying…");
                destroy(node);
                if (node.doId == null) {
                    onStep.accept(node, "destroyed");
                } else {
                    failures++;
                    onStep.accept(node, "no delete API — remove manually");
                }
            } catch (Exception ex) {
                failures++;
                onStep.accept(node, "destroy failed: " + ex.getMessage());
            }
        }
        return failures;
    }

    /** Destroys the cloud resource behind a node (the node stays designed). */
    public void destroy(InfraNode node) throws IOException, InterruptedException {
        if (node.doId == null) {
            return;
        }
        String path = resourcePath(node.kind, node.doId);
        if (path != null) {
            send("DELETE", path, "");
            node.doId = null;
        }
    }

    private static String compact(String text) {
        if (text == null) {
            return "unknown error";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 160 ? oneLine.substring(0, 160) + "…" : oneLine;
    }
}

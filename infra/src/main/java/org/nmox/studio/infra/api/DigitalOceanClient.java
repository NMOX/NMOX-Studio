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

/**
 * The live wire to DigitalOcean's v2 REST API. Executes deploy plans
 * (resolving cross-step id/ip placeholders as resources come up),
 * imports existing resources, and destroys per node. Without a token
 * everything stays in dry-run; with one, DEPLOY is real.
 */
public final class DigitalOceanClient {

    public static final String API = "https://api.digitalocean.com";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(id|ip)-of:([^}]+)}");

    private final HttpClient http = org.nmox.studio.core.http.HttpClientFactory.shared();

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
                if (node != null && ips.containsKey(node.id) && ips.get(node.id) != null) {
                    node.ip = ips.get(node.id); // remember it: the SSH command needs it later
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
        HttpResponse<java.io.InputStream> response =
                http.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
        // bounded read: ofString() buffered the whole body, so a hostile or
        // misconfigured endpoint behind a secret token could OOM the IDE. A
        // DO API list response is small; 8 MB is orders of magnitude past it.
        String text;
        try (java.io.InputStream in = response.body()) {
            text = new String(in.readNBytes(8 * 1024 * 1024),
                    java.nio.charset.StandardCharsets.UTF_8);
        }
        if (response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + compact(text));
        }
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

    /** One list endpoint: where to GET, the JSON array key, the kind it maps to, and which fields carry id/name. */
    record Source(String path, String listKey, NodeKind kind, String idKey, String nameKey) {
    }

    /** A resource discovered by a list call: enough to place a live node. */
    record Imported(NodeKind kind, String doId, String label, String ip,
            Map<String, String> props) {

        Imported(NodeKind kind, String doId, String label, String ip) {
            this(kind, doId, label, ip, Map.of());
        }
    }

    /** DigitalOcean's list endpoints, in a stable grid-friendly order. */
    static List<Source> digitalOceanSources() {
        return List.of(
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
    }

    /** Hetzner Cloud v1 list endpoints (relative to api.hetzner.cloud/v1). */
    static List<Source> hetznerSources() {
        return List.of(
                new Source("/servers", "servers", NodeKind.HZ_SERVER, "id", "name"),
                new Source("/networks", "networks", NodeKind.HZ_NETWORK, "id", "name"),
                new Source("/load_balancers", "load_balancers", NodeKind.HZ_LB, "id", "name"),
                new Source("/volumes", "volumes", NodeKind.HZ_VOLUME, "id", "name"),
                new Source("/firewalls", "firewalls", NodeKind.HZ_FIREWALL, "id", "name"),
                new Source("/floating_ips", "floating_ips", NodeKind.HZ_FLOATING_IP, "id", "name"));
    }

    /**
     * Maps a provider's list-response JSON to importable records — the
     * testable seam (a JSON string in, no network). Understands DO
     * droplet public-IP nesting and Hetzner's public_net.ipv4, so synced
     * compute nodes carry their IP for the SSH context menu. Returns []
     * for an absent or non-array key.
     */
    static List<Imported> parseListResponse(Source source, String json) {
        List<Imported> out = new java.util.ArrayList<>();
        JSONArray items = new JSONObject(json).optJSONArray(source.listKey());
        if (items == null) {
            return out;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || !item.has(source.idKey())) {
                continue;
            }
            String doId = String.valueOf(item.get(source.idKey()));
            String label;
            if (source.kind() == NodeKind.APP_PLATFORM) {
                JSONObject spec = item.optJSONObject("spec");
                label = spec != null ? spec.optString("name", doId) : doId;
            } else {
                label = item.optString(source.nameKey(), doId);
            }
            out.add(new Imported(source.kind(), doId, label, publicIpOf(source.kind(), item)));
        }
        return out;
    }

    /** Extracts a public IPv4 from a list item, per provider shape; null when none. */
    private static String publicIpOf(NodeKind kind, JSONObject item) {
        if (kind == NodeKind.DROPLET || kind == NodeKind.GPU_DROPLET) {
            JSONObject nets = item.optJSONObject("networks");
            JSONArray v4 = nets != null ? nets.optJSONArray("v4") : null;
            for (int k = 0; v4 != null && k < v4.length(); k++) {
                JSONObject net = v4.getJSONObject(k);
                if ("public".equals(net.optString("type"))) {
                    return net.optString("ip_address", null);
                }
            }
        } else if (kind == NodeKind.HZ_SERVER) {
            JSONObject net = item.optJSONObject("public_net");
            JSONObject ipv4 = net != null ? net.optJSONObject("ipv4") : null;
            String ip = ipv4 != null ? ipv4.optString("ip", "") : "";
            return ip.isEmpty() ? null : ip;
        }
        return null;
    }

    /** Parses GET /zones — Cloudflare wraps its lists in "result". */
    static List<String> parseZoneIds(String json) {
        JSONArray items = new JSONObject(json).optJSONArray("result");
        List<String> zones = new java.util.ArrayList<>();
        for (int i = 0; items != null && i < items.length(); i++) {
            String id = items.getJSONObject(i).optString("id", "");
            if (!id.isBlank()) {
                zones.add(id);
            }
        }
        return zones;
    }

    /**
     * Parses GET /zones/{zoneId}/dns_records. Every record carries its
     * zoneId prop — without it a CF node resolves no resource path, so
     * no drift check and no destroy. Labels read "type name → content".
     */
    static List<Imported> parseDnsRecords(String zoneId, String json) {
        JSONArray items = new JSONObject(json).optJSONArray("result");
        List<Imported> out = new java.util.ArrayList<>();
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String id = item.optString("id", "");
            if (id.isBlank()) {
                continue;
            }
            String type = item.optString("type", "?");
            String name = item.optString("name", "");
            String label = truncateLabel(type + " " + name + " → " + item.optString("content", ""));
            Map<String, String> props = Map.of(
                    "zoneId", zoneId,
                    "name", name,
                    "recordType", type,
                    "proxied", String.valueOf(item.optBoolean("proxied", false)));
            out.add(new Imported(NodeKind.CF_DNS_RECORD, id, label, null, props));
        }
        return out;
    }

    /** Node labels stay readable: long DNS contents get an ellipsis. */
    static String truncateLabel(String label) {
        return label.length() > 46 ? label.substring(0, 45) + "…" : label;
    }

    /** Per-provider sync outcome: nodes imported, or the failure message. */
    public record SyncOutcome(int imported, String error) {

        public boolean failed() {
            return error != null;
        }
    }

    /** How one provider's sync runs — a seam so tests can fake failures. */
    interface SyncFn {

        int sync(CloudProvider provider, InfraGraph graph) throws Exception;
    }

    /**
     * Syncs every given provider in order, isolating failures: one
     * cloud's outage or bad token never aborts the others. Outcomes
     * come back per provider, in sweep order, for honest reporting.
     */
    public java.util.LinkedHashMap<CloudProvider, SyncOutcome> syncAll(
            java.util.Collection<CloudProvider> providers, InfraGraph graph,
            java.util.function.Consumer<CloudProvider> onStart) {
        return sweep(providers, graph, onStart, this::sync);
    }

    static java.util.LinkedHashMap<CloudProvider, SyncOutcome> sweep(
            java.util.Collection<CloudProvider> providers, InfraGraph graph,
            java.util.function.Consumer<CloudProvider> onStart, SyncFn fn) {
        var outcomes = new java.util.LinkedHashMap<CloudProvider, SyncOutcome>();
        for (CloudProvider provider : providers) {
            if (onStart != null) {
                onStart.accept(provider);
            }
            try {
                outcomes.put(provider, new SyncOutcome(fn.sync(provider, graph), null));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                outcomes.put(provider, new SyncOutcome(0, "interrupted"));
                break;
            } catch (Exception ex) {
                outcomes.put(provider, new SyncOutcome(0, compact(ex.getMessage())));
            }
        }
        return outcomes;
    }

    /**
     * Imports one provider's EXISTING live resources as nodes laid out
     * in a grid below whatever the canvas already holds, marked live.
     * Resources whose id is already on the canvas refresh in place
     * instead of duplicating. Returns the count of newly imported nodes.
     */
    public int sync(CloudProvider provider, InfraGraph graph)
            throws IOException, InterruptedException {
        Grid grid = new Grid(graph);
        return switch (provider) {
            case DIGITALOCEAN -> syncProvider(provider, digitalOceanSources(), graph, grid);
            case HETZNER -> syncProvider(provider, hetznerSources(), graph, grid);
            case CLOUDFLARE -> syncCloudflare(graph, grid);
        };
    }

    /** The set of providers a sync would call: those with a token. */
    public static java.util.Set<CloudProvider> providersToSync(
            java.util.function.Predicate<CloudProvider> hasToken) {
        java.util.Set<CloudProvider> out = new java.util.LinkedHashSet<>();
        for (CloudProvider provider : CloudProvider.values()) {
            if (hasToken.test(provider)) {
                out.add(provider);
            }
        }
        return out;
    }

    /** Runs a provider's list endpoints, placing each new resource as a live node. */
    private int syncProvider(CloudProvider provider, List<Source> sources,
            InfraGraph graph, Grid grid) throws IOException, InterruptedException {
        // Hetzner caps per_page at 50; DigitalOcean allows 100
        int pageSize = provider == CloudProvider.DIGITALOCEAN ? 100 : 50;
        int imported = 0;
        for (Source source : sources) {
            String sep = source.path().contains("?") ? "&" : "?";
            String json = send(provider, "GET",
                    source.path() + sep + "per_page=" + pageSize, "").toString();
            for (Imported record : parseListResponse(source, json)) {
                if (place(graph, grid, record)) {
                    imported++;
                }
            }
            grid.newBand();
        }
        return imported;
    }

    /**
     * Cloudflare sync: zones first, then each zone's DNS records placed
     * as CF_DNS_RECORD nodes stamped with their zoneId — exactly what
     * lets drift and destroy address them later. R2 buckets stay out:
     * their API exposes no per-bucket id the designer could act on.
     */
    private int syncCloudflare(InfraGraph graph, Grid grid)
            throws IOException, InterruptedException {
        int imported = 0;
        String zonesJson = send(CloudProvider.CLOUDFLARE, "GET",
                "/zones?per_page=50", "").toString();
        for (String zoneId : parseZoneIds(zonesJson)) {
            String recordsJson = send(CloudProvider.CLOUDFLARE, "GET",
                    "/zones/" + zoneId + "/dns_records?per_page=100", "").toString();
            for (Imported record : parseDnsRecords(zoneId, recordsJson)) {
                if (place(graph, grid, record)) {
                    imported++;
                }
            }
        }
        grid.newBand();
        return imported;
    }

    /**
     * Places one imported resource as a live node — unless its id is
     * already on the canvas, in which case the existing node refreshes
     * in place: status, ip and blank props (a lost zoneId) update, but
     * values the user set deliberately stay untouched.
     */
    static boolean place(InfraGraph graph, Grid grid, Imported record) {
        InfraNode existing = graph.getNodes().stream()
                .filter(n -> record.doId().equals(n.doId)).findFirst().orElse(null);
        if (existing != null) {
            if (record.ip() != null) {
                existing.ip = record.ip();
            }
            record.props().forEach((k, v) -> {
                if (existing.props.getOrDefault(k, "").isBlank()) {
                    existing.props.put(k, v);
                }
            });
            graph.setStatus(existing, "live");
            return false;
        }
        InfraNode node = graph.addNode(record.kind(), grid.x, grid.y);
        node.label = record.label();
        node.doId = record.doId();
        node.ip = record.ip();
        node.props.putAll(record.props());
        graph.setStatus(node, "live");
        grid.advance();
        return true;
    }

    /** Grid layout cursor shared across a provider's sources so imported nodes don't overlap. */
    static final class Grid {
        int x = 80;
        int y = 60;
        boolean placedInBand;

        /** Seeds below the canvas's current extent: synced nodes never stack on existing ones. */
        Grid(InfraGraph graph) {
            for (InfraNode node : graph.getNodes()) {
                y = Math.max(y, node.y + 90);
            }
        }

        void advance() {
            placedInBand = true;
            x += 190;
            if (x > 900) {
                x = 80;
                y += 90;
            }
        }

        /** Starts a fresh row after a source/provider that placed anything. */
        void newBand() {
            if (placedInBand) {
                x = 80;
                y += 90;
                placedInBand = false;
            }
        }
    }

    /**
     * The REST path addressing a node's live resource - shared by
     * destroy and drift checks; null when the provider offers no
     * per-resource endpoint (R2 buckets) or the node lacks the context
     * the path needs (a Cloudflare record without its zone).
     */
    static String resourcePath(org.nmox.studio.infra.model.NodeKind kind, String id) {
        return resourcePath(kind, id, Map.of());
    }

    /** Same, with the node's props for kinds whose path needs context. */
    static String resourcePath(org.nmox.studio.infra.model.NodeKind kind, String id,
            Map<String, String> props) {
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
            // Hetzner Cloud v1 (relative to api.hetzner.cloud/v1)
            case HZ_SERVER -> "/servers/" + id;
            case HZ_NETWORK -> "/networks/" + id;
            case HZ_LB -> "/load_balancers/" + id;
            case HZ_VOLUME -> "/volumes/" + id;
            case HZ_FIREWALL -> "/firewalls/" + id;
            case HZ_FLOATING_IP -> "/floating_ips/" + id;
            // Cloudflare records live under their zone; no zone, no path
            case CF_DNS_RECORD -> {
                String zone = props.getOrDefault("zoneId", "");
                yield zone.isBlank() ? null : "/zones/" + zone + "/dns_records/" + id;
            }
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
            String path = resourcePath(node.kind, node.doId, node.props);
            if (path == null) {
                onStatus.accept(node, "unverifiable (no read API)");
                continue;
            }
            try {
                JSONObject response = send(node.kind.provider(), "GET", path, "");
                String ip = extractPublicIp(node.kind, response);
                if (ip != null) {
                    node.ip = ip; // ssh parity: the context menu offers root@ip
                }
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
        String path = resourcePath(node.kind, node.doId, node.props);
        if (path != null) {
            send(node.kind.provider(), "DELETE", path, "");
            node.doId = null;
        }
    }

    /**
     * Pulls a public IPv4 out of a per-resource GET, per provider shape:
     * DO droplets nest it in networks.v4, Hetzner servers in
     * public_net.ipv4. Null for kinds without one, or odd responses.
     */
    static String extractPublicIp(NodeKind kind, JSONObject response) {
        try {
            return switch (kind) {
                case DROPLET, GPU_DROPLET -> {
                    JSONArray v4 = response.getJSONObject("droplet")
                            .getJSONObject("networks").optJSONArray("v4");
                    String found = null;
                    for (int i = 0; v4 != null && i < v4.length(); i++) {
                        JSONObject net = v4.getJSONObject(i);
                        if ("public".equals(net.optString("type"))) {
                            found = net.optString("ip_address", null);
                        }
                    }
                    yield found;
                }
                case HZ_SERVER -> {
                    String ip = response.getJSONObject("server")
                            .getJSONObject("public_net").getJSONObject("ipv4")
                            .optString("ip", "");
                    yield ip.isEmpty() ? null : ip;
                }
                default -> null;
            };
        } catch (RuntimeException ex) {
            return null; // no IP is fine; a drift check never fails over it
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

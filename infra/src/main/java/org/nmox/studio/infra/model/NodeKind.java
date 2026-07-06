package org.nmox.studio.infra.model;

import java.awt.Color;
import java.util.List;
import java.util.Set;

/**
 * The palette: DigitalOcean's offerings, 1:1. Each kind carries its
 * Node-RED-style category color, its configurable properties with
 * defaults, an estimated monthly price for the cost meter, and the
 * relationship rules that decide which wires make sense.
 */
public enum NodeKind {

    // ---- Compute ----
    DROPLET("Droplet", Category.COMPUTE,
            List.of(
                Prop.choice("size", "Size", "s-1vcpu-1gb",
                    "s-1vcpu-512mb-10gb", "s-1vcpu-1gb", "s-1vcpu-2gb", "s-2vcpu-2gb",
                    "s-2vcpu-4gb", "s-4vcpu-8gb", "s-8vcpu-16gb", "c-2", "c-4", "g-2vcpu-8gb"),
                Prop.choice("region", "Region", "nyc3", regions()),
                Prop.choice("image", "Image", "ubuntu-24-04-x64",
                    "ubuntu-24-04-x64", "ubuntu-22-04-x64", "debian-12-x64", "fedora-40-x64",
                    "rockylinux-9-x64", "docker-20-04"),
                Prop.bool("backups", "Backups", false),
                Prop.bool("monitoring", "Monitoring agent", true),
                Prop.text("userData", "Cloud-init (user_data)", "")),
            6.0),
    GPU_DROPLET("GPU Droplet", Category.COMPUTE,
            List.of(
                Prop.choice("size", "GPU size", "gpu-h100x1-80gb",
                    "gpu-h100x1-80gb", "gpu-h100x8-640gb", "gpu-l40sx1-48gb", "gpu-6000adax1-48gb"),
                Prop.choice("region", "Region", "nyc2", "nyc2", "tor1", "atl1"),
                Prop.choice("image", "Image", "gpu-h100x1-base", "gpu-h100x1-base", "ubuntu-24-04-x64")),
            1830.0),
    KUBERNETES("Kubernetes", Category.COMPUTE,
            List.of(
                Prop.choice("region", "Region", "nyc3", regions()),
                Prop.choice("nodeSize", "Node size", "s-2vcpu-4gb",
                    "s-2vcpu-2gb", "s-2vcpu-4gb", "s-4vcpu-8gb", "s-8vcpu-16gb"),
                Prop.number("nodeCount", "Node count", 3),
                Prop.bool("ha", "HA control plane", false),
                Prop.bool("autoscale", "Autoscale pool", false)),
            72.0),
    APP_PLATFORM("App Platform", Category.COMPUTE,
            List.of(
                Prop.text("repo", "GitHub repo", "owner/app"),
                Prop.choice("instance", "Instance", "basic-xxs",
                    "basic-xxs", "basic-xs", "basic-s", "professional-xs", "professional-s"),
                Prop.number("instances", "Instance count", 1),
                Prop.choice("region", "Region", "nyc", "nyc", "ams", "fra", "lon", "sfo", "sgp", "syd", "tor", "blr")),
            5.0),
    FUNCTIONS("Functions", Category.COMPUTE,
            List.of(
                Prop.text("namespaceLabel", "Namespace label", "fn-namespace"),
                Prop.choice("region", "Region", "nyc1", "nyc1", "ams3", "fra1", "lon1", "sfo3", "sgp1", "syd1", "tor1", "blr1")),
            0.0),
    GRADIENT_AI("Gradient AI", Category.COMPUTE,
            List.of(
                Prop.text("agentName", "Agent name", "assistant"),
                Prop.choice("model", "Model", "llama3.3-70b-instruct",
                    "llama3.3-70b-instruct", "anthropic-claude-3.5-sonnet", "openai-gpt-4o",
                    "deepseek-r1-distill-llama-70b"),
                Prop.choice("region", "Region", "tor1", "tor1", "nyc2")),
            0.0),

    // ---- Networking ----
    VPC("VPC", Category.NETWORKING,
            List.of(
                Prop.text("ipRange", "IP range", "10.10.0.0/20"),
                Prop.choice("region", "Region", "nyc3", regions())),
            0.0),
    LOAD_BALANCER("Load Balancer", Category.NETWORKING,
            List.of(
                Prop.choice("size", "Size", "lb-small", "lb-small", "lb-medium", "lb-large"),
                Prop.choice("region", "Region", "nyc3", regions()),
                Prop.choice("forwardingRule", "Forwarding", "http-80",
                    "http-80", "https-443", "tcp-22", "http2-443"),
                Prop.text("healthPath", "Health check path", "/")),
            12.0),
    FIREWALL("Firewall", Category.NETWORKING,
            List.of(
                Prop.text("inbound", "Inbound (port/cidr)", "22/0.0.0.0/0, 80/0.0.0.0/0, 443/0.0.0.0/0"),
                Prop.text("outbound", "Outbound", "all/0.0.0.0/0")),
            0.0),
    RESERVED_IP("Reserved IP", Category.NETWORKING,
            List.of(Prop.choice("region", "Region", "nyc3", regions())),
            0.0),
    DOMAIN("Domain / DNS", Category.NETWORKING,
            List.of(
                Prop.text("name", "Domain name", "example.com"),
                Prop.choice("recordType", "Record type", "A", "A", "AAAA", "CNAME", "MX", "TXT"),
                Prop.text("recordName", "Record name", "@")),
            0.0),
    CDN("CDN", Category.NETWORKING,
            List.of(Prop.number("ttl", "Cache TTL (s)", 3600)),
            0.0),

    // ---- Storage ----
    VOLUME("Volume", Category.STORAGE,
            List.of(
                Prop.number("sizeGb", "Size (GiB)", 100),
                Prop.choice("region", "Region", "nyc3", regions()),
                Prop.choice("fs", "Filesystem", "ext4", "ext4", "xfs")),
            10.0),
    SPACES("Spaces Bucket", Category.STORAGE,
            List.of(
                Prop.text("bucket", "Bucket name", "my-bucket"),
                Prop.choice("region", "Region", "nyc3", "nyc3", "ams3", "fra1", "sfo3", "sgp1", "syd1"),
                Prop.bool("publicRead", "Public read", false)),
            5.0),
    CONTAINER_REGISTRY("Container Registry", Category.STORAGE,
            List.of(
                Prop.choice("tier", "Tier", "basic", "starter", "basic", "professional"),
                Prop.choice("region", "Region", "nyc3", "nyc3", "ams3", "fra1", "sfo3", "sgp1", "syd1")),
            5.0),

    // ---- Databases ----
    DB_POSTGRES("PostgreSQL", Category.DATABASES, dbProps("16"), 15.0),
    DB_MYSQL("MySQL", Category.DATABASES, dbProps("8"), 15.0),
    DB_MONGODB("MongoDB", Category.DATABASES, dbProps("7"), 15.0),
    DB_VALKEY("Valkey (Redis)", Category.DATABASES, dbProps("8"), 15.0),
    DB_KAFKA("Kafka", Category.DATABASES, dbProps("3.8"), 147.0),
    DB_OPENSEARCH("OpenSearch", Category.DATABASES, dbProps("2"), 49.0),

    // ---- Security & Ops ----
    SSH_KEY("SSH Key", Category.OPS,
            List.of(
                Prop.text("name", "Key name", "workstation"),
                Prop.text("publicKey", "Public key", "ssh-ed25519 AAAA...")),
            0.0),
    CERTIFICATE("Certificate", Category.OPS,
            List.of(
                Prop.text("name", "Name", "site-cert"),
                Prop.choice("certType", "Type", "lets_encrypt", "lets_encrypt", "custom"),
                Prop.text("dnsNames", "DNS names", "example.com")),
            0.0),
    MONITOR_ALERT("Monitor Alert", Category.OPS,
            List.of(
                Prop.choice("metric", "Metric", "v1/insights/droplet/cpu",
                    "v1/insights/droplet/cpu", "v1/insights/droplet/memory_utilization_percent",
                    "v1/insights/droplet/disk_utilization_percent"),
                Prop.number("threshold", "Threshold %", 80),
                Prop.text("email", "Alert email", "ops@example.com")),
            0.0),

    // ---- Hetzner Cloud ----
    HZ_SERVER("HZ Server", Category.HETZNER,
            List.of(
                Prop.choice("serverType", "Type", "cx22",
                    "cx22", "cx32", "cx42", "cpx11", "cpx21", "cpx31", "cax11", "cax21"),
                Prop.choice("location", "Location", "fsn1", "fsn1", "nbg1", "hel1", "ash", "hil", "sin"),
                Prop.choice("image", "Image", "ubuntu-24.04",
                    "ubuntu-24.04", "ubuntu-22.04", "debian-12", "fedora-40", "rocky-9"),
                Prop.text("userData", "Cloud-init (user_data)", "")),
            4.59),
    HZ_NETWORK("HZ Network", Category.HETZNER,
            List.of(Prop.text("ipRange", "IP range", "10.0.0.0/16")),
            0.0),
    HZ_LB("HZ Load Balancer", Category.HETZNER,
            List.of(
                Prop.choice("lbType", "Type", "lb11", "lb11", "lb21", "lb31"),
                Prop.choice("location", "Location", "fsn1", "fsn1", "nbg1", "hel1", "ash", "hil")),
            6.41),
    HZ_VOLUME("HZ Volume", Category.HETZNER,
            List.of(
                Prop.number("sizeGb", "Size (GiB)", 50),
                Prop.choice("location", "Location", "fsn1", "fsn1", "nbg1", "hel1", "ash", "hil"),
                Prop.choice("format", "Filesystem", "ext4", "ext4", "xfs")),
            2.60),
    HZ_FIREWALL("HZ Firewall", Category.HETZNER,
            List.of(Prop.text("inbound", "Inbound (port/cidr)", "22/0.0.0.0/0, 80/0.0.0.0/0, 443/0.0.0.0/0")),
            0.0),
    HZ_FLOATING_IP("HZ Floating IP", Category.HETZNER,
            List.of(Prop.choice("homeLocation", "Home location", "fsn1", "fsn1", "nbg1", "hel1", "ash", "hil")),
            3.92),

    // ---- Cloudflare ----
    CF_DNS_RECORD("CF DNS Record", Category.CLOUDFLARE,
            List.of(
                Prop.text("zoneId", "Zone ID", ""),
                Prop.text("name", "Record name", "app.example.com"),
                Prop.choice("recordType", "Type", "A", "A", "AAAA", "CNAME", "TXT"),
                Prop.bool("proxied", "Proxied (orange cloud)", true)),
            0.0),
    CF_R2_BUCKET("CF R2 Bucket", Category.CLOUDFLARE,
            List.of(
                Prop.text("accountId", "Account ID", ""),
                Prop.text("bucket", "Bucket name", "my-bucket")),
            0.0);

    /** Node-RED-ish category palette. */
    public enum Category {
        COMPUTE(new Color(0x53, 0x9E, 0xF6)),
        NETWORKING(new Color(0xE8, 0x9A, 0x3C)),
        STORAGE(new Color(0x8F, 0x6B, 0xD6)),
        DATABASES(new Color(0x4E, 0xC9, 0x8B)),
        OPS(new Color(0xD6, 0x5C, 0x6E)),
        HETZNER(new Color(0xD5, 0x0C, 0x2D)),
        CLOUDFLARE(new Color(0xF6, 0x82, 0x1F));

        public final Color color;

        Category(Color color) {
            this.color = color;
        }
    }

    /** A configurable property with a default and optional choices. */
    public record Prop(String key, String label, String type, String defaultValue, List<String> choices) {

        public Prop {
            choices = List.copyOf(choices); // a caller's list must not mutate the catalog
        }

        static Prop text(String key, String label, String dflt) {
            return new Prop(key, label, "text", dflt, List.of());
        }

        static Prop number(String key, String label, int dflt) {
            return new Prop(key, label, "number", String.valueOf(dflt), List.of());
        }

        static Prop bool(String key, String label, boolean dflt) {
            return new Prop(key, label, "bool", String.valueOf(dflt), List.of());
        }

        static Prop choice(String key, String label, String dflt, String... choices) {
            return new Prop(key, label, "choice", dflt, List.of(choices));
        }
    }

    private static String[] regions() {
        return new String[]{
            "nyc1", "nyc3", "sfo3", "ams3", "fra1", "lon1", "sgp1", "blr1", "syd1", "tor1"};
    }

    private static List<Prop> dbProps(String version) {
        return List.of(
                Prop.choice("size", "Size", "db-s-1vcpu-1gb",
                    "db-s-1vcpu-1gb", "db-s-1vcpu-2gb", "db-s-2vcpu-4gb", "db-s-4vcpu-8gb"),
                Prop.choice("region", "Region", "nyc3", regions()),
                Prop.number("nodes", "Standby nodes + primary", 1),
                Prop.text("version", "Engine version", version));
    }

    /**
     * Wire rules: each entry lists the kinds this kind may wire INTO.
     * A wire A->B reads "A serves B": VPC->Droplet places it, Droplet->LB
     * joins the backend pool, Spaces->CDN sets the origin.
     */
    public Set<NodeKind> wiresInto() {
        return switch (this) {
            case VPC -> Set.of(DROPLET, GPU_DROPLET, KUBERNETES, LOAD_BALANCER,
                    DB_POSTGRES, DB_MYSQL, DB_MONGODB, DB_VALKEY, DB_KAFKA, DB_OPENSEARCH);
            case SSH_KEY -> Set.of(DROPLET, GPU_DROPLET);
            case FIREWALL -> Set.of(DROPLET, GPU_DROPLET);
            case VOLUME -> Set.of(DROPLET, GPU_DROPLET);
            case RESERVED_IP -> Set.of(DROPLET, GPU_DROPLET);
            case DROPLET, GPU_DROPLET -> Set.of(LOAD_BALANCER, DOMAIN, MONITOR_ALERT, CF_DNS_RECORD);
            case KUBERNETES -> Set.of(MONITOR_ALERT);
            case LOAD_BALANCER -> Set.of(DOMAIN, CF_DNS_RECORD);
            case APP_PLATFORM, FUNCTIONS -> Set.of(DOMAIN, CF_DNS_RECORD);
            case SPACES -> Set.of(CDN);
            case CERTIFICATE -> Set.of(LOAD_BALANCER, CDN);
            case CONTAINER_REGISTRY -> Set.of(KUBERNETES, APP_PLATFORM);
            case DB_POSTGRES, DB_MYSQL, DB_MONGODB, DB_VALKEY, DB_KAFKA, DB_OPENSEARCH ->
                Set.of(APP_PLATFORM, KUBERNETES);
            case GRADIENT_AI -> Set.of(APP_PLATFORM, FUNCTIONS);
            case HZ_NETWORK -> Set.of(HZ_SERVER, HZ_LB);
            case HZ_FIREWALL -> Set.of(HZ_SERVER);
            case HZ_VOLUME -> Set.of(HZ_SERVER);
            case HZ_FLOATING_IP -> Set.of(HZ_SERVER);
            case HZ_SERVER -> Set.of(HZ_LB, CF_DNS_RECORD);
            case HZ_LB -> Set.of(CF_DNS_RECORD);
            case CDN, DOMAIN, MONITOR_ALERT, CF_DNS_RECORD, CF_R2_BUCKET -> Set.of();
        };
    }

    /**
     * Relations that are post-create attachments (both ends must exist
     * before an action call) rather than creation-time references.
     */
    public boolean attachesTo(NodeKind target) {
        if ((this == VOLUME || this == RESERVED_IP || this == FIREWALL)
                && (target == DROPLET || target == GPU_DROPLET)) {
            return true;
        }
        return (this == HZ_VOLUME || this == HZ_FLOATING_IP || this == HZ_FIREWALL)
                && target == HZ_SERVER;
    }

    /** Which cloud's API realizes this kind. */
    public org.nmox.studio.infra.api.CloudProvider provider() {
        return switch (category) {
            case HETZNER -> org.nmox.studio.infra.api.CloudProvider.HETZNER;
            case CLOUDFLARE -> org.nmox.studio.infra.api.CloudProvider.CLOUDFLARE;
            default -> org.nmox.studio.infra.api.CloudProvider.DIGITALOCEAN;
        };
    }

    private final String displayName;
    private final Category category;
    private final List<Prop> props;
    private final double monthlyUsd;

    NodeKind(String displayName, Category category, List<Prop> props, double monthlyUsd) {
        this.displayName = displayName;
        this.category = category;
        this.props = props;
        this.monthlyUsd = monthlyUsd;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Category getCategory() {
        return category;
    }

    public List<Prop> getProps() {
        return props;
    }

    /** Estimated monthly price with the node's current property values. */
    public double estimateMonthlyUsd(java.util.Map<String, String> values) {
        double base = monthlyUsd;
        return switch (this) {
            case DROPLET -> DROPLET_PRICES.getOrDefault(values.getOrDefault("size", "s-1vcpu-1gb"), base);
            case VOLUME -> 0.10 * parseInt(values.get("sizeGb"), 100);
            case HZ_VOLUME -> 0.052 * parseInt(values.get("sizeGb"), 50);
            case HZ_SERVER -> HZ_SERVER_PRICES.getOrDefault(values.getOrDefault("serverType", "cx22"), 4.59);
            case KUBERNETES -> {
                double node = DROPLET_PRICES.getOrDefault(values.getOrDefault("nodeSize", "s-2vcpu-4gb"), 24.0);
                double ha = Boolean.parseBoolean(values.getOrDefault("ha", "false")) ? 40.0 : 0.0;
                yield node * parseInt(values.get("nodeCount"), 3) + ha;
            }
            case DB_POSTGRES, DB_MYSQL, DB_MONGODB, DB_VALKEY -> {
                double size = DB_PRICES.getOrDefault(values.getOrDefault("size", "db-s-1vcpu-1gb"), 15.0);
                yield size * Math.max(1, parseInt(values.get("nodes"), 1));
            }
            default -> base;
        };
    }

    private static final java.util.Map<String, Double> DROPLET_PRICES = java.util.Map.ofEntries(
            java.util.Map.entry("s-1vcpu-512mb-10gb", 4.0),
            java.util.Map.entry("s-1vcpu-1gb", 6.0),
            java.util.Map.entry("s-1vcpu-2gb", 12.0),
            java.util.Map.entry("s-2vcpu-2gb", 18.0),
            java.util.Map.entry("s-2vcpu-4gb", 24.0),
            java.util.Map.entry("s-4vcpu-8gb", 48.0),
            java.util.Map.entry("s-8vcpu-16gb", 96.0),
            java.util.Map.entry("c-2", 42.0),
            java.util.Map.entry("c-4", 84.0),
            java.util.Map.entry("g-2vcpu-8gb", 63.0));

    private static final java.util.Map<String, Double> HZ_SERVER_PRICES = java.util.Map.of(
            "cx22", 4.59, "cx32", 8.09, "cx42", 19.52,
            "cpx11", 4.85, "cpx21", 8.59, "cpx31", 16.18,
            "cax11", 4.05, "cax21", 7.55);

    private static final java.util.Map<String, Double> DB_PRICES = java.util.Map.of(
            "db-s-1vcpu-1gb", 15.0,
            "db-s-1vcpu-2gb", 30.0,
            "db-s-2vcpu-4gb", 60.0,
            "db-s-4vcpu-8gb", 120.0);

    private static int parseInt(String value, int dflt) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ex) {
            return dflt;
        }
    }
}

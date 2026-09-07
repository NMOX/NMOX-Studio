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
@org.openide.util.NbBundle.Messages({
    "NodeKind_droplet=Droplet",
    "NodeKind_gpuDroplet=GPU Droplet",
    "NodeKind_kubernetes=Kubernetes",
    "NodeKind_appPlatform=App Platform",
    "NodeKind_functions=Functions",
    "NodeKind_gradientAi=Gradient AI",
    "NodeKind_vpc=VPC",
    "NodeKind_loadBalancer=Load Balancer",
    "NodeKind_firewall=Firewall",
    "NodeKind_reservedIp=Reserved IP",
    "NodeKind_domain=Domain / DNS",
    "NodeKind_cdn=CDN",
    "NodeKind_volume=Volume",
    "NodeKind_spaces=Spaces Bucket",
    "NodeKind_containerRegistry=Container Registry",
    "NodeKind_dbPostgres=PostgreSQL",
    "NodeKind_dbMysql=MySQL",
    "NodeKind_dbMongodb=MongoDB",
    "NodeKind_dbValkey=Valkey (Redis)",
    "NodeKind_dbKafka=Kafka",
    "NodeKind_dbOpensearch=OpenSearch",
    "NodeKind_sshKey=SSH Key",
    "NodeKind_certificate=Certificate",
    "NodeKind_monitorAlert=Monitor Alert",
    "NodeKind_hzServer=HZ Server",
    "NodeKind_hzNetwork=HZ Network",
    "NodeKind_hzLb=HZ Load Balancer",
    "NodeKind_hzVolume=HZ Volume",
    "NodeKind_hzFirewall=HZ Firewall",
    "NodeKind_hzFloatingIp=HZ Floating IP",
    "NodeKind_cfDnsRecord=CF DNS Record",
    "NodeKind_cfR2Bucket=CF R2 Bucket",
    "NodeKind_propSize=Size",
    "NodeKind_propRegion=Region",
    "NodeKind_propImage=Image",
    "NodeKind_propBackups=Backups",
    "NodeKind_propMonitoringAgent=Monitoring agent",
    "NodeKind_propCloudInitUserData=Cloud-init (user_data)",
    "NodeKind_propGpuSize=GPU size",
    "NodeKind_propNodeSize=Node size",
    "NodeKind_propNodeCount=Node count",
    "NodeKind_propHaControlPlane=HA control plane",
    "NodeKind_propAutoscalePool=Autoscale pool",
    "NodeKind_propGithubRepo=GitHub repo",
    "NodeKind_propInstance=Instance",
    "NodeKind_propInstanceCount=Instance count",
    "NodeKind_propNamespaceLabel=Namespace label",
    "NodeKind_propAgentName=Agent name",
    "NodeKind_propModel=Model",
    "NodeKind_propIpRange=IP range",
    "NodeKind_propForwarding=Forwarding",
    "NodeKind_propHealthCheckPath=Health check path",
    "NodeKind_propInboundPortCidr=Inbound (port/cidr)",
    "NodeKind_propOutbound=Outbound",
    "NodeKind_propDomainName=Domain name",
    "NodeKind_propRecordType=Record type",
    "NodeKind_propRecordName=Record name",
    "NodeKind_propCacheTtlS=Cache TTL (s)",
    "NodeKind_propSizeGib=Size (GiB)",
    "NodeKind_propFilesystem=Filesystem",
    "NodeKind_propBucketName=Bucket name",
    "NodeKind_propPublicRead=Public read",
    "NodeKind_propTier=Tier",
    "NodeKind_propKeyName=Key name",
    "NodeKind_propPublicKey=Public key",
    "NodeKind_propName=Name",
    "NodeKind_propType=Type",
    "NodeKind_propDnsNames=DNS names",
    "NodeKind_propMetric=Metric",
    "NodeKind_propThreshold=Threshold %",
    "NodeKind_propAlertEmail=Alert email",
    "NodeKind_propLocation=Location",
    "NodeKind_propHomeLocation=Home location",
    "NodeKind_propZoneId=Zone ID",
    "NodeKind_propProxiedOrangeCloud=Proxied (orange cloud)",
    "NodeKind_propAccountId=Account ID",
    "NodeKind_propStandbyNodesPrimary=Standby nodes + primary",
    "NodeKind_propEngineVersion=Engine version",
    "NodeKind_categoryCompute=COMPUTE",
    "NodeKind_categoryNetworking=NETWORKING",
    "NodeKind_categoryStorage=STORAGE",
    "NodeKind_categoryDatabases=DATABASES",
    "NodeKind_categoryOps=OPS",
    "NodeKind_categoryHetzner=HETZNER",
    "NodeKind_categoryCloudflare=CLOUDFLARE"
})
public enum NodeKind {

    // ---- Compute ----
    DROPLET(Bundle.NodeKind_droplet(), Category.COMPUTE,
            List.of(
                Prop.choice("size", Bundle.NodeKind_propSize(), "s-1vcpu-1gb",
                    "s-1vcpu-512mb-10gb", "s-1vcpu-1gb", "s-1vcpu-2gb", "s-2vcpu-2gb",
                    "s-2vcpu-4gb", "s-4vcpu-8gb", "s-8vcpu-16gb", "c-2", "c-4", "g-2vcpu-8gb"),
                Prop.choice("region", Bundle.NodeKind_propRegion(), "nyc3", regions()),
                Prop.choice("image", Bundle.NodeKind_propImage(), "ubuntu-24-04-x64",
                    "ubuntu-24-04-x64", "ubuntu-22-04-x64", "debian-12-x64", "fedora-40-x64",
                    "rockylinux-9-x64", "docker-20-04"),
                Prop.bool("backups", Bundle.NodeKind_propBackups(), false),
                Prop.bool("monitoring", Bundle.NodeKind_propMonitoringAgent(), true),
                Prop.text("userData", Bundle.NodeKind_propCloudInitUserData(), "")),
            6.0),
    GPU_DROPLET(Bundle.NodeKind_gpuDroplet(), Category.COMPUTE,
            List.of(
                Prop.choice("size", Bundle.NodeKind_propGpuSize(), "gpu-h100x1-80gb",
                    "gpu-h100x1-80gb", "gpu-h100x8-640gb", "gpu-l40sx1-48gb", "gpu-6000adax1-48gb"),
                Prop.choice("region", Bundle.NodeKind_propRegion(), "nyc2", "nyc2", "tor1", "atl1"),
                Prop.choice("image", Bundle.NodeKind_propImage(), "gpu-h100x1-base", "gpu-h100x1-base", "ubuntu-24-04-x64")),
            1830.0),
    KUBERNETES(Bundle.NodeKind_kubernetes(), Category.COMPUTE,
            List.of(
                Prop.choice("region", Bundle.NodeKind_propRegion(), "nyc3", regions()),
                Prop.choice("nodeSize", Bundle.NodeKind_propNodeSize(), "s-2vcpu-4gb",
                    "s-2vcpu-2gb", "s-2vcpu-4gb", "s-4vcpu-8gb", "s-8vcpu-16gb"),
                Prop.number("nodeCount", Bundle.NodeKind_propNodeCount(), 3),
                Prop.bool("ha", Bundle.NodeKind_propHaControlPlane(), false),
                Prop.bool("autoscale", Bundle.NodeKind_propAutoscalePool(), false)),
            72.0),
    APP_PLATFORM(Bundle.NodeKind_appPlatform(), Category.COMPUTE,
            List.of(
                Prop.text("repo", Bundle.NodeKind_propGithubRepo(), "owner/app"),
                Prop.choice("instance", Bundle.NodeKind_propInstance(), "basic-xxs",
                    "basic-xxs", "basic-xs", "basic-s", "professional-xs", "professional-s"),
                Prop.number("instances", Bundle.NodeKind_propInstanceCount(), 1),
                Prop.choice("region", Bundle.NodeKind_propRegion(), "nyc", "nyc", "ams", "fra", "lon", "sfo", "sgp", "syd", "tor", "blr")),
            5.0),
    FUNCTIONS(Bundle.NodeKind_functions(), Category.COMPUTE,
            List.of(
                Prop.text("namespaceLabel", Bundle.NodeKind_propNamespaceLabel(), "fn-namespace"),
                Prop.choice("region", Bundle.NodeKind_propRegion(), "nyc1", "nyc1", "ams3", "fra1", "lon1", "sfo3", "sgp1", "syd1", "tor1", "blr1")),
            0.0),
    GRADIENT_AI(Bundle.NodeKind_gradientAi(), Category.COMPUTE,
            List.of(
                Prop.text("agentName", Bundle.NodeKind_propAgentName(), "assistant"),
                Prop.choice("model", Bundle.NodeKind_propModel(), "llama3.3-70b-instruct",
                    "llama3.3-70b-instruct", "anthropic-claude-3.5-sonnet", "openai-gpt-4o",
                    "deepseek-r1-distill-llama-70b"),
                Prop.choice("region", Bundle.NodeKind_propRegion(), "tor1", "tor1", "nyc2")),
            0.0),

    // ---- Networking ----
    VPC(Bundle.NodeKind_vpc(), Category.NETWORKING,
            List.of(
                Prop.text("ipRange", Bundle.NodeKind_propIpRange(), "10.10.0.0/20"),
                Prop.choice("region", Bundle.NodeKind_propRegion(), "nyc3", regions())),
            0.0),
    LOAD_BALANCER(Bundle.NodeKind_loadBalancer(), Category.NETWORKING,
            List.of(
                Prop.choice("size", Bundle.NodeKind_propSize(), "lb-small", "lb-small", "lb-medium", "lb-large"),
                Prop.choice("region", Bundle.NodeKind_propRegion(), "nyc3", regions()),
                Prop.choice("forwardingRule", Bundle.NodeKind_propForwarding(), "http-80",
                    "http-80", "https-443", "tcp-22", "http2-443"),
                Prop.text("healthPath", Bundle.NodeKind_propHealthCheckPath(), "/")),
            12.0),
    FIREWALL(Bundle.NodeKind_firewall(), Category.NETWORKING,
            List.of(
                Prop.text("inbound", Bundle.NodeKind_propInboundPortCidr(), "22/0.0.0.0/0, 80/0.0.0.0/0, 443/0.0.0.0/0"),
                Prop.text("outbound", Bundle.NodeKind_propOutbound(), "all/0.0.0.0/0")),
            0.0),
    RESERVED_IP(Bundle.NodeKind_reservedIp(), Category.NETWORKING,
            List.of(Prop.choice("region", Bundle.NodeKind_propRegion(), "nyc3", regions())),
            0.0),
    DOMAIN(Bundle.NodeKind_domain(), Category.NETWORKING,
            List.of(
                Prop.text("name", Bundle.NodeKind_propDomainName(), "example.com"),
                Prop.choice("recordType", Bundle.NodeKind_propRecordType(), "A", "A", "AAAA", "CNAME", "MX", "TXT"),
                Prop.text("recordName", Bundle.NodeKind_propRecordName(), "@")),
            0.0),
    CDN(Bundle.NodeKind_cdn(), Category.NETWORKING,
            List.of(Prop.number("ttl", Bundle.NodeKind_propCacheTtlS(), 3600)),
            0.0),

    // ---- Storage ----
    VOLUME(Bundle.NodeKind_volume(), Category.STORAGE,
            List.of(
                Prop.number("sizeGb", Bundle.NodeKind_propSizeGib(), 100),
                Prop.choice("region", Bundle.NodeKind_propRegion(), "nyc3", regions()),
                Prop.choice("fs", Bundle.NodeKind_propFilesystem(), "ext4", "ext4", "xfs")),
            10.0),
    SPACES(Bundle.NodeKind_spaces(), Category.STORAGE,
            List.of(
                Prop.text("bucket", Bundle.NodeKind_propBucketName(), "my-bucket"),
                Prop.choice("region", Bundle.NodeKind_propRegion(), "nyc3", "nyc3", "ams3", "fra1", "sfo3", "sgp1", "syd1"),
                Prop.bool("publicRead", Bundle.NodeKind_propPublicRead(), false)),
            5.0),
    CONTAINER_REGISTRY(Bundle.NodeKind_containerRegistry(), Category.STORAGE,
            List.of(
                Prop.choice("tier", Bundle.NodeKind_propTier(), "basic", "starter", "basic", "professional"),
                Prop.choice("region", Bundle.NodeKind_propRegion(), "nyc3", "nyc3", "ams3", "fra1", "sfo3", "sgp1", "syd1")),
            5.0),

    // ---- Databases ----
    DB_POSTGRES(Bundle.NodeKind_dbPostgres(), Category.DATABASES, dbProps("16"), 15.0),
    DB_MYSQL(Bundle.NodeKind_dbMysql(), Category.DATABASES, dbProps("8"), 15.0),
    DB_MONGODB(Bundle.NodeKind_dbMongodb(), Category.DATABASES, dbProps("7"), 15.0),
    DB_VALKEY(Bundle.NodeKind_dbValkey(), Category.DATABASES, dbProps("8"), 15.0),
    DB_KAFKA(Bundle.NodeKind_dbKafka(), Category.DATABASES, dbProps("3.8"), 147.0),
    DB_OPENSEARCH(Bundle.NodeKind_dbOpensearch(), Category.DATABASES, dbProps("2"), 49.0),

    // ---- Security & Ops ----
    SSH_KEY(Bundle.NodeKind_sshKey(), Category.OPS,
            List.of(
                Prop.text("name", Bundle.NodeKind_propKeyName(), "workstation"),
                Prop.text("publicKey", Bundle.NodeKind_propPublicKey(), "ssh-ed25519 AAAA...")),
            0.0),
    CERTIFICATE(Bundle.NodeKind_certificate(), Category.OPS,
            List.of(
                Prop.text("name", Bundle.NodeKind_propName(), "site-cert"),
                Prop.choice("certType", Bundle.NodeKind_propType(), "lets_encrypt", "lets_encrypt", "custom"),
                Prop.text("dnsNames", Bundle.NodeKind_propDnsNames(), "example.com")),
            0.0),
    MONITOR_ALERT(Bundle.NodeKind_monitorAlert(), Category.OPS,
            List.of(
                Prop.choice("metric", Bundle.NodeKind_propMetric(), "v1/insights/droplet/cpu",
                    "v1/insights/droplet/cpu", "v1/insights/droplet/memory_utilization_percent",
                    "v1/insights/droplet/disk_utilization_percent"),
                Prop.number("threshold", Bundle.NodeKind_propThreshold(), 80),
                Prop.text("email", Bundle.NodeKind_propAlertEmail(), "ops@example.com")),
            0.0),

    // ---- Hetzner Cloud ----
    HZ_SERVER(Bundle.NodeKind_hzServer(), Category.HETZNER,
            List.of(
                Prop.choice("serverType", Bundle.NodeKind_propType(), "cx22",
                    "cx22", "cx32", "cx42", "cpx11", "cpx21", "cpx31", "cax11", "cax21"),
                Prop.choice("location", Bundle.NodeKind_propLocation(), "fsn1", "fsn1", "nbg1", "hel1", "ash", "hil", "sin"),
                Prop.choice("image", Bundle.NodeKind_propImage(), "ubuntu-24.04",
                    "ubuntu-24.04", "ubuntu-22.04", "debian-12", "fedora-40", "rocky-9"),
                Prop.text("userData", Bundle.NodeKind_propCloudInitUserData(), "")),
            4.59),
    HZ_NETWORK(Bundle.NodeKind_hzNetwork(), Category.HETZNER,
            List.of(Prop.text("ipRange", Bundle.NodeKind_propIpRange(), "10.0.0.0/16")),
            0.0),
    HZ_LB(Bundle.NodeKind_hzLb(), Category.HETZNER,
            List.of(
                Prop.choice("lbType", Bundle.NodeKind_propType(), "lb11", "lb11", "lb21", "lb31"),
                Prop.choice("location", Bundle.NodeKind_propLocation(), "fsn1", "fsn1", "nbg1", "hel1", "ash", "hil")),
            6.41),
    HZ_VOLUME(Bundle.NodeKind_hzVolume(), Category.HETZNER,
            List.of(
                Prop.number("sizeGb", Bundle.NodeKind_propSizeGib(), 50),
                Prop.choice("location", Bundle.NodeKind_propLocation(), "fsn1", "fsn1", "nbg1", "hel1", "ash", "hil"),
                Prop.choice("format", Bundle.NodeKind_propFilesystem(), "ext4", "ext4", "xfs")),
            2.60),
    HZ_FIREWALL(Bundle.NodeKind_hzFirewall(), Category.HETZNER,
            List.of(Prop.text("inbound", Bundle.NodeKind_propInboundPortCidr(), "22/0.0.0.0/0, 80/0.0.0.0/0, 443/0.0.0.0/0")),
            0.0),
    HZ_FLOATING_IP(Bundle.NodeKind_hzFloatingIp(), Category.HETZNER,
            List.of(Prop.choice("homeLocation", Bundle.NodeKind_propHomeLocation(), "fsn1", "fsn1", "nbg1", "hel1", "ash", "hil")),
            3.92),

    // ---- Cloudflare ----
    CF_DNS_RECORD(Bundle.NodeKind_cfDnsRecord(), Category.CLOUDFLARE,
            List.of(
                Prop.text("zoneId", Bundle.NodeKind_propZoneId(), ""),
                Prop.text("name", Bundle.NodeKind_propRecordName(), "app.example.com"),
                Prop.choice("recordType", Bundle.NodeKind_propType(), "A", "A", "AAAA", "CNAME", "TXT"),
                Prop.bool("proxied", Bundle.NodeKind_propProxiedOrangeCloud(), true)),
            0.0),
    CF_R2_BUCKET(Bundle.NodeKind_cfR2Bucket(), Category.CLOUDFLARE,
            List.of(
                Prop.text("accountId", Bundle.NodeKind_propAccountId(), ""),
                Prop.text("bucket", Bundle.NodeKind_propBucketName(), "my-bucket")),
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

        /** The palette's group caption for this category. */
        public String getDisplayName() {
            return switch (this) {
                case COMPUTE -> Bundle.NodeKind_categoryCompute();
                case NETWORKING -> Bundle.NodeKind_categoryNetworking();
                case STORAGE -> Bundle.NodeKind_categoryStorage();
                case DATABASES -> Bundle.NodeKind_categoryDatabases();
                case OPS -> Bundle.NodeKind_categoryOps();
                case HETZNER -> Bundle.NodeKind_categoryHetzner();
                case CLOUDFLARE -> Bundle.NodeKind_categoryCloudflare();
            };
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
                Prop.choice("size", Bundle.NodeKind_propSize(), "db-s-1vcpu-1gb",
                    "db-s-1vcpu-1gb", "db-s-1vcpu-2gb", "db-s-2vcpu-4gb", "db-s-4vcpu-8gb"),
                Prop.choice("region", Bundle.NodeKind_propRegion(), "nyc3", regions()),
                Prop.number("nodes", Bundle.NodeKind_propStandbyNodesPrimary(), 1),
                Prop.text("version", Bundle.NodeKind_propEngineVersion(), version));
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
            // a managed database serving a plain droplet is the single most
            // common DO pairing — it was missing while App Platform and
            // Kubernetes were representable (v1.271.0, the DevOps walk's
            // catalog find). The wire orders creation (db first) and carries
            // no creation-time reference: droplet create takes no db id, and
            // the planner's provider loop reads only VPC/SSH_KEY kinds.
            case DB_POSTGRES, DB_MYSQL, DB_MONGODB, DB_VALKEY, DB_KAFKA, DB_OPENSEARCH ->
                Set.of(APP_PLATFORM, KUBERNETES, DROPLET, GPU_DROPLET);
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

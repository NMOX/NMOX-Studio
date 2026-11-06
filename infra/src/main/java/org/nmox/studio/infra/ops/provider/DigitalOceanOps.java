package org.nmox.studio.infra.ops.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.infra.api.CloudHttp;
import org.nmox.studio.infra.api.CloudProvider;
import org.openide.util.lookup.ServiceProvider;

/**
 * DigitalOcean as an {@link OpsProvider}: droplets through the v2 API, the
 * token from the keychain the Infra Designer already keeps. Every host the
 * account has is listed — a droplet made elsewhere is as real as one made
 * here — and the Hub's own carry the {@code nmox-ops} tag as a fact.
 *
 * <p>The transport is a seam ({@link CloudHttp.Transport}) so a test can
 * answer every path from a map and prove the parsing and the request
 * bodies without a token or a bill.
 */
@ServiceProvider(service = OpsProvider.class, position = 100)
public final class DigitalOceanOps implements OpsProvider {

    public static final String ID = "digitalocean";
    /** The tag a Hub-made droplet carries. */
    public static final String TAG = "nmox-ops";
    /** Distributions whose images carry the packages cloud-init installs. */
    static final List<String> OFFERED_DISTRIBUTIONS = List.of("Ubuntu", "Debian");

    private final CloudHttp.Transport api;

    public DigitalOceanOps() {
        this(CloudHttp.over(CloudProvider.DIGITALOCEAN));
    }

    DigitalOceanOps(CloudHttp.Transport api) {
        this.api = api;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return CloudProvider.DIGITALOCEAN.displayName();
    }

    @Override
    public boolean simulated() {
        return false;
    }

    @Override
    public boolean configured() {
        return CloudProvider.DIGITALOCEAN.hasToken();
    }

    @Override
    public ProviderOptions options() throws IOException, InterruptedException {
        JSONObject regions = api.send("GET", "/v2/regions?per_page=200", null);
        JSONObject sizes = api.send("GET", "/v2/sizes?per_page=200", null);
        JSONObject images = api.send("GET", "/v2/images?type=distribution&per_page=200", null);
        JSONObject keys = api.send("GET", "/v2/account/keys?per_page=200", null);
        return parseOptions(regions, sizes, images, keys);
    }

    static ProviderOptions parseOptions(JSONObject regions, JSONObject sizes, JSONObject images, JSONObject keys) {
        List<ProviderOptions.Region> rs = new ArrayList<>();
        JSONArray ra = regions.optJSONArray("regions");
        for (int i = 0; ra != null && i < ra.length(); i++) {
            JSONObject r = ra.getJSONObject(i);
            rs.add(new ProviderOptions.Region(r.optString("slug", ""), r.optString("name", ""),
                    r.optBoolean("available", false)));
        }
        List<ProviderOptions.Size> ss = new ArrayList<>();
        JSONArray sa = sizes.optJSONArray("sizes");
        for (int i = 0; sa != null && i < sa.length(); i++) {
            JSONObject s = sa.getJSONObject(i);
            if (!s.optBoolean("available", true)) {
                continue;
            }
            List<String> where = new ArrayList<>();
            JSONArray wr = s.optJSONArray("regions");
            for (int j = 0; wr != null && j < wr.length(); j++) {
                where.add(wr.getString(j));
            }
            ss.add(new ProviderOptions.Size(s.optString("slug", ""), s.optDouble("price_monthly", Double.NaN),
                    s.optDouble("price_hourly", Double.NaN), s.optInt("vcpus", 0), s.optInt("memory", 0),
                    s.optInt("disk", 0), where));
        }
        ss.sort(java.util.Comparator.comparingDouble(ProviderOptions.Size::priceMonthly));
        List<ProviderOptions.Image> is = new ArrayList<>();
        JSONArray ia = images.optJSONArray("images");
        for (int i = 0; ia != null && i < ia.length(); i++) {
            JSONObject im = ia.getJSONObject(i);
            String dist = im.optString("distribution", "");
            String slug = im.optString("slug", "");
            if (slug.isEmpty() || !OFFERED_DISTRIBUTIONS.contains(dist)) {
                continue;
            }
            is.add(new ProviderOptions.Image(slug, im.optString("name", slug), dist));
        }
        is.sort(java.util.Comparator.comparing(ProviderOptions.Image::slug).reversed());
        List<ProviderOptions.SshKey> ks = new ArrayList<>();
        JSONArray ka = keys.optJSONArray("ssh_keys");
        for (int i = 0; ka != null && i < ka.length(); i++) {
            JSONObject k = ka.getJSONObject(i);
            ks.add(new ProviderOptions.SshKey(String.valueOf(k.opt("id")), k.optString("name", ""),
                    k.optString("fingerprint", "")));
        }
        return new ProviderOptions(rs, ss, is, ks);
    }

    @Override
    public List<HostRecord> listHosts() throws IOException, InterruptedException {
        List<HostRecord> out = new ArrayList<>();
        JSONObject page = api.send("GET", "/v2/droplets?per_page=100", null);
        JSONArray ds = page.optJSONArray("droplets");
        for (int i = 0; ds != null && i < ds.length(); i++) {
            out.add(parseDroplet(ds.getJSONObject(i)));
        }
        return out;
    }

    @Override
    public HostRecord createHost(HostSpec spec) throws IOException, InterruptedException {
        JSONObject body = creationBody(spec);
        JSONObject response = api.send("POST", "/v2/droplets", body.toString());
        JSONObject droplet = response.optJSONObject("droplet");
        if (droplet == null) {
            throw new IOException("DigitalOcean answered without a droplet: " + CloudHttp.compact(response.toString()));
        }
        return parseDroplet(droplet);
    }

    /** The request body; a test reads it to prove the tag, the key ids and the cloud-init travel. */
    static JSONObject creationBody(HostSpec spec) {
        JSONObject body = new JSONObject()
                .put("name", spec.name())
                .put("region", spec.region())
                .put("size", spec.size())
                .put("image", spec.image())
                .put("monitoring", true)
                .put("tags", new JSONArray().put(TAG));
        JSONArray keys = new JSONArray();
        for (String k : spec.sshKeyIds()) {
            keys.put(k.chars().allMatch(Character::isDigit) ? (Object) Long.parseLong(k) : k);
        }
        body.put("ssh_keys", keys);
        if (!spec.userData().isBlank()) {
            body.put("user_data", spec.userData());
        }
        return body;
    }

    @Override
    public HostRecord readHost(String providerId) throws IOException, InterruptedException {
        try {
            JSONObject response = api.send("GET", "/v2/droplets/" + providerId, null);
            JSONObject droplet = response.optJSONObject("droplet");
            return droplet == null ? null : parseDroplet(droplet);
        } catch (IOException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("HTTP 404:")) {
                return null;
            }
            throw ex;
        }
    }

    @Override
    public void destroyHost(String providerId) throws IOException, InterruptedException {
        api.send("DELETE", "/v2/droplets/" + providerId, null);
    }

    @Override
    public String addSshKey(String name, String publicKey) throws IOException, InterruptedException {
        JSONObject body = new JSONObject().put("name", name).put("public_key", publicKey.trim());
        JSONObject response = api.send("POST", "/v2/account/keys", body.toString());
        JSONObject key = response.optJSONObject("ssh_key");
        if (key == null) {
            throw new IOException("DigitalOcean answered without a key: " + CloudHttp.compact(response.toString()));
        }
        return String.valueOf(key.opt("id"));
    }

    /** One droplet object as the API prints it, in a list or alone. */
    static HostRecord parseDroplet(JSONObject d) {
        String id = String.valueOf(d.opt("id"));
        String status = d.optString("status", "");
        String ip = null;
        JSONObject nets = d.optJSONObject("networks");
        JSONArray v4 = nets != null ? nets.optJSONArray("v4") : null;
        for (int i = 0; v4 != null && i < v4.length(); i++) {
            JSONObject n = v4.getJSONObject(i);
            if ("public".equals(n.optString("type"))) {
                ip = n.optString("ip_address", null);
            }
        }
        Map<String, String> facts = new LinkedHashMap<>();
        JSONObject region = d.optJSONObject("region");
        if (region != null) {
            facts.put("region", region.optString("name", region.optString("slug", "")));
        }
        facts.put("size", d.optString("size_slug", ""));
        JSONObject image = d.optJSONObject("image");
        if (image != null) {
            facts.put("image", image.optString("name", image.optString("slug", "")));
        }
        facts.put("created", d.optString("created_at", ""));
        JSONArray tags = d.optJSONArray("tags");
        boolean ours = false;
        for (int i = 0; tags != null && i < tags.length(); i++) {
            if (TAG.equals(tags.optString(i))) {
                ours = true;
            }
        }
        facts.put("made here", ours ? "yes" : "no");
        String vpc = d.optString("vpc_uuid", "");
        if (!vpc.isEmpty()) {
            facts.put("vpc", vpc);
        }
        facts.values().removeIf(String::isEmpty);
        return new HostRecord(id, d.optString("name", id), ip, "active".equals(status), status, facts);
    }
}

package org.nmox.studio.infra.ops.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.infra.api.CloudHttp;
import org.nmox.studio.infra.api.CloudProvider;
import org.openide.util.lookup.ServiceProvider;

/**
 * Hetzner Cloud as an {@link OpsProvider}: servers through the v1 API,
 * the token from the keychain entry the Infra Designer's Tokens… already
 * keeps for Hetzner ({@link CloudProvider#HETZNER}).
 *
 * <p>What maps to what (the long form is {@code docs/engineering/ops-hub.md}):
 * a region is a Hetzner <em>location</em>, a size is a <em>server type</em>
 * at one location (its price differs by location, so one type is offered
 * once per location it can be had in), the Hub's {@code nmox-ops} tag is
 * a <em>label</em> (Hetzner has labels, not tags), and the lifecycle is
 * the server's own {@code status}. Like the DigitalOcean provider it lists
 * every server of the project the token belongs to — a server made in
 * Hetzner's console is as real as one made here — and the label is the
 * fact "made here", never a filter.
 *
 * <p>Every path is relative to {@link CloudProvider#apiBase()}, which
 * already ends in {@code /v1}. Every list is PAGED and every page is read
 * ({@code meta.pagination.next_page} until it is null): the Hub removes
 * whatever a listing no longer names, so a listing that stopped at page
 * one would announce real servers as gone.
 *
 * <p>No live call was made while this class was written — a server bills.
 * The shapes are Hetzner's published OpenAPI document; the test says
 * which.
 */
@ServiceProvider(service = OpsProvider.class, position = 200)
public final class HetznerOps implements OpsProvider {

    public static final String ID = "hetzner";
    /** The label key a Hub-made server (and a key the Hub uploaded) carries; the same word as DigitalOcean's tag. */
    public static final String LABEL = DigitalOceanOps.TAG;
    /** A label needs a value; the key's presence is what is read back. */
    static final String LABEL_VALUE = "true";
    /** {@code os_flavor}s whose images carry the packages cloud-init installs. */
    static final List<String> OFFERED_FLAVORS = List.of("ubuntu", "debian");
    /** Hetzner's own maximum for {@code per_page}. */
    static final int PER_PAGE = 50;
    /** 2,000 entries, the DigitalOcean provider's cap: past it a listing is refused whole, never given in part. */
    static final int MAX_PAGES = 40;

    private static final Pattern NUMERIC_ID = Pattern.compile("[0-9]{1,19}");
    private static final Pattern HTTP_ERROR = Pattern.compile("HTTP (\\d{3}): (.*)", Pattern.DOTALL);
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([^\"\\\\]{1,80})\"");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.){1,200})");

    private final CloudHttp.Transport api;

    public HetznerOps() {
        this(CloudHttp.over(CloudProvider.HETZNER));
    }

    HetznerOps(CloudHttp.Transport api) {
        this.api = api;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return CloudProvider.HETZNER.displayName();
    }

    @Override
    public boolean simulated() {
        return false;
    }

    @Override
    public boolean configured() {
        return CloudProvider.HETZNER.hasToken();
    }

    @Override
    public boolean takesToken() {
        return true;
    }

    @Override
    public void storeToken(String token) {
        CloudProvider.HETZNER.storeToken(token);
    }

    // ---- the catalogue ----

    @Override
    public ProviderOptions options() throws IOException, InterruptedException {
        List<JSONObject> locations = pages("/locations", "locations");
        List<JSONObject> types = pages("/server_types", "server_types");
        List<JSONObject> images = pages("/images?type=system&status=available", "images");
        List<JSONObject> keys = pages("/ssh_keys", "ssh_keys");
        JSONObject pricing = send("GET", "/pricing", null);
        return parseOptions(locations, types, images, keys, pricing);
    }

    static ProviderOptions parseOptions(List<JSONObject> locations, List<JSONObject> types, List<JSONObject> images,
            List<JSONObject> keys, JSONObject pricingAnswer) throws IOException {
        JSONObject pricing = pricingAnswer.optJSONObject("pricing");
        String currency = pricing == null ? "" : pricing.optString("currency", "");
        if (currency.isBlank()) {
            // a figure with no currency is not a price, and the next step is a question that names a bill
            throw new IOException("Hetzner Cloud's price list names no currency");
        }
        Map<String, double[]> ipv4 = primaryIpv4Prices(pricing);

        List<ProviderOptions.Region> rs = new ArrayList<>();
        for (JSONObject l : locations) {
            String slug = l.optString("name", "");
            if (slug.isEmpty()) {
                continue;
            }
            String city = l.optString("city", "");
            String country = l.optString("country", "");
            String name = city.isEmpty() ? l.optString("description", slug)
                    : country.isEmpty() ? city : city + ", " + country;
            rs.add(new ProviderOptions.Region(slug, name, true));
        }

        List<ProviderOptions.Size> ss = new ArrayList<>();
        for (JSONObject t : types) {
            String slug = t.optString("name", "");
            if (slug.isEmpty() || t.optJSONObject("deprecation") != null) {
                continue;
            }
            Map<String, JSONObject> where = byName(t.optJSONArray("locations"));
            JSONArray prices = t.optJSONArray("prices");
            for (int i = 0; prices != null && i < prices.length(); i++) {
                JSONObject p = prices.optJSONObject(i);
                String location = p == null ? "" : p.optString("location", "");
                if (location.isEmpty() || !offeredAt(t, where, location)) {
                    continue;
                }
                double monthly = gross(p.optJSONObject("price_monthly"));
                double hourly = gross(p.optJSONObject("price_hourly"));
                if (Double.isNaN(monthly)) {
                    continue; // a size with no price is not offered: the confirm could not name its bill
                }
                // the Hub asks for a public IPv4 (ssh needs an address), and Hetzner prices that address
                // apart from the server type: the bill is both
                double[] address = ipv4.get(location);
                if (address != null) {
                    monthly += address[0];
                    hourly += address[1];
                }
                ss.add(new ProviderOptions.Size(slug, monthly, hourly, (int) Math.round(t.optDouble("cores", 0)),
                        (int) Math.round(t.optDouble("memory", 0) * 1024), (int) Math.round(t.optDouble("disk", 0)),
                        List.of(location)));
            }
        }
        ss.sort(java.util.Comparator.comparingDouble(ProviderOptions.Size::priceMonthly)
                .thenComparing(ProviderOptions.Size::slug));

        // one image name serves both architectures (the API lists it once for x86 and once for arm)
        Map<String, ProviderOptions.Image> byName = new LinkedHashMap<>();
        for (JSONObject im : images) {
            String flavor = im.optString("os_flavor", "");
            String name = im.isNull("name") ? "" : im.optString("name", "");
            if (name.isEmpty() || !OFFERED_FLAVORS.contains(flavor) || im.optJSONObject("deprecation") != null
                    || !"available".equals(im.optString("status", "available"))) {
                continue;
            }
            String version = im.isNull("os_version") ? "" : im.optString("os_version", "");
            byName.putIfAbsent(name, new ProviderOptions.Image(name, version.isEmpty() ? name : version,
                    flavor.substring(0, 1).toUpperCase(Locale.ROOT) + flavor.substring(1)));
        }
        List<ProviderOptions.Image> is = new ArrayList<>(byName.values());
        is.sort(java.util.Comparator.comparing(ProviderOptions.Image::slug).reversed());

        List<ProviderOptions.SshKey> ks = new ArrayList<>();
        for (JSONObject k : keys) {
            ks.add(new ProviderOptions.SshKey(String.valueOf(k.opt("id")), k.optString("name", ""),
                    k.optString("fingerprint", "")));
        }
        return new ProviderOptions(rs, ss, is, ks, currency);
    }

    /**
     * A server type is supported only in the locations its {@code locations}
     * array lists, and not offered where that entry is deprecated or says
     * {@code available: false}. An answer without the array (older shape)
     * offers it wherever it has a price.
     */
    private static boolean offeredAt(JSONObject type, Map<String, JSONObject> where, String location) {
        if (type.optJSONArray("locations") == null) {
            return true;
        }
        JSONObject at = where.get(location);
        return at != null && at.optJSONObject("deprecation") == null && at.optBoolean("available", true);
    }

    private static Map<String, JSONObject> byName(JSONArray array) {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o != null) {
                out.put(o.optString("name", ""), o);
            }
        }
        return out;
    }

    /** location → {monthly, hourly} gross, for a primary IP of type {@code ipv4}; empty when the list names none. */
    private static Map<String, double[]> primaryIpv4Prices(JSONObject pricing) {
        Map<String, double[]> out = new LinkedHashMap<>();
        JSONArray kinds = pricing.optJSONArray("primary_ips");
        for (int i = 0; kinds != null && i < kinds.length(); i++) {
            JSONObject kind = kinds.optJSONObject(i);
            if (kind == null || !"ipv4".equals(kind.optString("type"))) {
                continue;
            }
            JSONArray prices = kind.optJSONArray("prices");
            for (int j = 0; prices != null && j < prices.length(); j++) {
                JSONObject p = prices.optJSONObject(j);
                if (p == null) {
                    continue;
                }
                double monthly = gross(p.optJSONObject("price_monthly"));
                double hourly = gross(p.optJSONObject("price_hourly"));
                if (!Double.isNaN(monthly) && !Double.isNaN(hourly)) {
                    out.put(p.optString("location", ""), new double[]{monthly, hourly});
                }
            }
        }
        return out;
    }

    /**
     * Hetzner states a price as {@code {"net": "3.2900", "gross": "3.9151"}}
     * — decimal STRINGS, so nothing is lost to a float on the wire. Gross
     * is what the account is billed (VAT included). NaN when it is not a
     * non-negative number: a size without a price is then not offered.
     */
    static double gross(JSONObject price) {
        if (price == null) {
            return Double.NaN;
        }
        Object gross = price.opt("gross");
        if (!(gross instanceof String) && !(gross instanceof Number)) {
            return Double.NaN;
        }
        try {
            double value = new java.math.BigDecimal(String.valueOf(gross).trim()).doubleValue();
            return value >= 0 && !Double.isInfinite(value) ? value : Double.NaN;
        } catch (NumberFormatException notANumber) {
            return Double.NaN;
        }
    }

    // ---- hosts ----

    @Override
    public List<HostRecord> listHosts() throws IOException, InterruptedException {
        List<HostRecord> out = new ArrayList<>();
        for (JSONObject server : pages("/servers", "servers")) {
            out.add(parseServer(server));
        }
        return out;
    }

    @Override
    public HostRecord createHost(HostSpec spec) throws IOException, InterruptedException {
        JSONObject response = send("POST", "/servers", creationBody(spec).toString());
        JSONObject server = response.optJSONObject("server");
        if (server == null) {
            // the answer is NOT quoted: a create without keys is answered with a root password
            throw new IOException("Hetzner Cloud answered without a server");
        }
        return parseServer(server);
    }

    /** The request body; a test reads it to prove the label, the key ids, the address and the cloud-init travel. */
    static JSONObject creationBody(HostSpec spec) {
        JSONObject body = new JSONObject()
                .put("name", spec.name())
                .put("server_type", spec.size())
                .put("image", spec.image())
                .put("location", spec.region())
                .put("start_after_create", true)
                .put("labels", new JSONObject().put(LABEL, LABEL_VALUE))
                // ssh is how everything after this is done: say that the public IPv4 is wanted, not assumed
                .put("public_net", new JSONObject().put("enable_ipv4", true).put("enable_ipv6", true));
        JSONArray keys = new JSONArray();
        for (String k : spec.sshKeyIds()) {
            keys.put(NUMERIC_ID.matcher(k).matches() ? (Object) Long.parseLong(k) : k);
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
            JSONObject response = send("GET", "/servers/" + numeric(providerId), null);
            JSONObject server = response.optJSONObject("server");
            if (server == null) {
                // a 200 with no server in it is not "gone": only the API's own 404 is
                throw new IOException("Hetzner Cloud answered without a server");
            }
            return parseServer(server);
        } catch (IOException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("HTTP 404:")) {
                return null;
            }
            throw ex;
        }
    }

    @Override
    public void destroyHost(String providerId) throws IOException, InterruptedException {
        send("DELETE", "/servers/" + numeric(providerId), null);
    }

    @Override
    public String addSshKey(String name, String publicKey) throws IOException, InterruptedException {
        JSONObject body = new JSONObject().put("name", name).put("public_key", publicKey.trim())
                .put("labels", new JSONObject().put(LABEL, LABEL_VALUE));
        JSONObject response = send("POST", "/ssh_keys", body.toString());
        JSONObject key = response.optJSONObject("ssh_key");
        if (key == null) {
            throw new IOException("Hetzner Cloud answered without a key");
        }
        return String.valueOf(key.opt("id"));
    }

    /** One server object as the API prints it, in a list, alone, or as a create's answer. */
    static HostRecord parseServer(JSONObject s) {
        String id = String.valueOf(s.opt("id"));
        String status = s.optString("status", "");
        String ip = null;
        JSONObject net = s.optJSONObject("public_net");
        JSONObject v4 = net == null ? null : net.optJSONObject("ipv4");
        if (v4 != null && !v4.optString("ip", "").isEmpty()) {
            ip = v4.getString("ip");
        }
        Map<String, String> facts = new LinkedHashMap<>();
        JSONObject location = s.optJSONObject("location");
        if (location != null) {
            String city = location.optString("city", "");
            facts.put("region", city.isEmpty() ? location.optString("name", "")
                    : city + " (" + location.optString("name", "") + ")");
        }
        JSONObject type = s.optJSONObject("server_type");
        if (type != null) {
            facts.put("size", type.optString("name", ""));
        }
        JSONObject image = s.optJSONObject("image");
        if (image != null) {
            facts.put("image", image.optString("description", image.isNull("name") ? "" : image.optString("name", "")));
        }
        facts.put("created", s.optString("created", ""));
        JSONObject labels = s.optJSONObject("labels");
        facts.put("made here", labels != null && labels.has(LABEL) ? "yes" : "no");
        facts.values().removeIf(String::isEmpty);
        return new HostRecord(id, s.optString("name", id), ip, phase(status), status, facts);
    }

    /**
     * Hetzner's documented server statuses as the Hub reads them:
     * {@code running} runs; {@code initializing} and {@code starting} are
     * a server on its way up; everything else — {@code off},
     * {@code stopping}, {@code deleting}, {@code migrating},
     * {@code rebuilding}, {@code unknown}, and any word this product has
     * not been taught — is "not running", which the Hub shows as
     * unreachable with Hetzner's own word as the reason. Never a guess
     * that an unknown word means "coming up": that would sweep forever.
     */
    static HostRecord.Phase phase(String status) {
        return switch (status == null ? "" : status) {
            case "running" -> HostRecord.Phase.RUNNING;
            case "initializing", "starting" -> HostRecord.Phase.COMING_UP;
            default -> HostRecord.Phase.NOT_RUNNING;
        };
    }

    // ---- the wire ----

    /**
     * Every page of a list endpoint. Follows the page number the API names
     * as next until it names none; an answer that names a next page but
     * goes on past the cap, or a full page that carries no pagination at
     * all, is refused whole — a partial list is worse than none here.
     */
    private List<JSONObject> pages(String path, String key) throws IOException, InterruptedException {
        List<JSONObject> out = new ArrayList<>();
        Set<Integer> read = new LinkedHashSet<>();
        int page = 1;
        while (true) {
            if (read.size() >= MAX_PAGES) {
                throw new IOException("the account lists more than " + (MAX_PAGES * PER_PAGE) + " " + key
                        + "; the Hub does not list that many");
            }
            read.add(page);
            JSONObject answer = send("GET", path + (path.contains("?") ? "&" : "?") + "page=" + page
                    + "&per_page=" + PER_PAGE, null);
            JSONArray items = answer.optJSONArray(key);
            for (int i = 0; items != null && i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) {
                    out.add(item);
                }
            }
            JSONObject meta = answer.optJSONObject("meta");
            JSONObject pagination = meta == null ? null : meta.optJSONObject("pagination");
            if (pagination == null) {
                if (items != null && items.length() >= PER_PAGE) {
                    throw new IOException("Hetzner Cloud sent a full page of " + key
                            + " without saying whether another follows");
                }
                return out;
            }
            if (pagination.isNull("next_page")) {
                return out;
            }
            int next = pagination.optInt("next_page", -1);
            if (next < 1 || read.contains(next)) {
                throw new IOException("Hetzner Cloud named page " + pagination.opt("next_page") + " of " + key
                        + " as the one after page " + page);
            }
            page = next;
        }
    }

    /** One round trip, with Hetzner's {@code {"error":{"code":…,"message":…}}} said as a sentence. */
    private JSONObject send(String method, String path, String body) throws IOException, InterruptedException {
        try {
            return api.send(method, path, body);
        } catch (IOException ex) {
            throw readable(ex);
        }
    }

    /**
     * {@code HTTP 422: {"error":{"code":"invalid_input","message":"…"}}}
     * becomes {@code HTTP 422: … (invalid_input)}: the feed and a dialog
     * get Hetzner's sentence and not its JSON. The {@code HTTP nnn:} head
     * is kept — it is how a 404 is told from every other failure. The
     * transport cuts a body at 160 characters, so a cut one is read by
     * pattern; anything unrecognised passes through as it came.
     */
    static IOException readable(IOException ex) {
        Matcher m = ex.getMessage() == null ? null : HTTP_ERROR.matcher(ex.getMessage());
        if (m == null || !m.matches()) {
            return ex;
        }
        String status = m.group(1);
        String rest = m.group(2);
        String code = null;
        String message = null;
        try {
            JSONObject error = new JSONObject(rest).optJSONObject("error");
            if (error != null) {
                code = error.optString("code", null);
                message = error.optString("message", null);
            }
        } catch (JSONException cut) {
            Matcher c = ERROR_CODE.matcher(rest);
            Matcher t = ERROR_MESSAGE.matcher(rest);
            code = c.find() ? c.group(1) : null;
            message = t.find() ? t.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
        }
        if (message == null || message.isBlank()) {
            return ex;
        }
        IOException said = new IOException("HTTP " + status + ": " + CloudHttp.compact(message)
                + (code == null || code.isBlank() ? "" : " (" + code + ")"));
        said.initCause(ex);
        return said;
    }

    /** A server id is a number in this API; nothing else is spliced into a path. */
    private static String numeric(String providerId) throws IOException {
        if (providerId == null || !NUMERIC_ID.matcher(providerId).matches()) {
            throw new IOException("not a Hetzner Cloud server id: " + providerId);
        }
        return providerId;
    }
}

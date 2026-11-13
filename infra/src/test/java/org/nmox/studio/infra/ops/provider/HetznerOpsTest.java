package org.nmox.studio.infra.ops.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.nmox.studio.infra.api.CloudHttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Hetzner Cloud provider against a transport that answers from a map
 * — no token, no bill.
 *
 * <p><b>Where the shapes come from.</b> Every answer below is written to
 * Hetzner's published OpenAPI document
 * ({@code https://docs.hetzner.cloud/cloud.spec.json}, the source of
 * {@code https://docs.hetzner.cloud/}, read on 2026-11-13). Read from it,
 * and relied on here:
 * <ul>
 *   <li>the base {@code https://api.hetzner.cloud/v1} and bearer-token authentication;</li>
 *   <li>{@code GET /servers} with {@code page} and {@code per_page} (maximum 50), answering
 *       {@code {"servers":[…],"meta":{"pagination":{"page","per_page","previous_page","next_page",
 *       "last_page","total_entries"}}}} where {@code next_page} is null on the last page — the same
 *       {@code meta.pagination} on {@code /server_types}, {@code /locations}, {@code /images} and
 *       {@code /ssh_keys};</li>
 *   <li>a server object: {@code id} (integer), {@code name}, {@code status} (one of {@code running,
 *       initializing, starting, stopping, off, deleting, migrating, rebuilding, unknown}),
 *       {@code created}, {@code public_net.ipv4} (an object with {@code ip}, or null),
 *       {@code server_type.name}, {@code location.{name,city,country,description}},
 *       {@code image.{name,description}}, {@code labels} (an object of key/value strings);</li>
 *   <li>{@code POST /servers} taking {@code name}, {@code server_type}, {@code image} (required) and
 *       {@code location}, {@code ssh_keys} (ids as integers or names as strings), {@code labels},
 *       {@code user_data} (limited to 32 KiB), {@code start_after_create},
 *       {@code public_net.{enable_ipv4,enable_ipv6}}, answering 201 with {@code {"server":…,
 *       "action":…,"next_actions":[…],"root_password":string|null}};</li>
 *   <li>{@code GET /servers/{id}} answering {@code {"server":…}}; {@code DELETE /servers/{id}}
 *       answering {@code {"action":…}};</li>
 *   <li>{@code GET /server_types}: {@code name}, {@code cores}, {@code memory} and {@code disk} in GB,
 *       {@code deprecation} (object or null), {@code locations[].{name,available,deprecation}}, and
 *       {@code prices[]} per location with {@code price_hourly} and {@code price_monthly} as
 *       {@code {"net":"1.0000","gross":"1.1900"}} — decimal STRINGS;</li>
 *   <li>{@code GET /locations}: {@code name}, {@code description}, {@code country}, {@code city};</li>
 *   <li>{@code GET /images?type=system&status=available}: {@code name} (string or null),
 *       {@code description}, {@code os_flavor}, {@code os_version}, {@code status},
 *       {@code deprecation}, {@code architecture};</li>
 *   <li>{@code GET /ssh_keys} and {@code POST /ssh_keys} ({@code name}, {@code public_key},
 *       {@code labels}) answering {@code {"ssh_key":{"id","name","fingerprint",…}}};</li>
 *   <li>{@code GET /pricing}: {@code pricing.currency} (ISO 4217) and
 *       {@code pricing.primary_ips[].{type,prices[].{location,price_hourly,price_monthly}}};</li>
 *   <li>errors as {@code {"error":{"code":"…","message":"…","details":…}}} with the documented
 *       codes ({@code not_found} 404, {@code invalid_input} 422, {@code uniqueness_error} 409,
 *       {@code server_error} 500);</li>
 *   <li>labels: a key of up to 63 characters from {@code [a-zA-Z0-9_.-]}, a value that may be empty.</li>
 * </ul>
 *
 * <p><b>None of it was observed live.</b> No request was sent to
 * {@code api.hetzner.cloud} with a token while this was written: a server
 * bills its owner, and a listing needs the owner's token. What the
 * running API does where the document is silent (the order of a listing,
 * the exact words of an error, whether an account in another currency
 * states it in {@code pricing.currency}) is not known here.
 */
class HetznerOpsTest {

    static final String SERVER = "{\"id\":4242,\"name\":\"nmox-a1\",\"status\":\"running\",\"created\":\"2026-11-06T10:00:00Z\","
            + "\"public_net\":{\"ipv4\":{\"id\":7,\"ip\":\"203.0.113.9\",\"blocked\":false,\"dns_ptr\":\"static.example\"},"
            + "\"ipv6\":{\"ip\":\"2001:db8::/64\"},\"floating_ips\":[],\"firewalls\":[]},\"private_net\":[],"
            + "\"server_type\":{\"id\":1,\"name\":\"cx22\",\"cores\":2,\"memory\":4,\"disk\":40},"
            + "\"location\":{\"id\":1,\"name\":\"fsn1\",\"description\":\"Falkenstein DC Park 1\",\"country\":\"DE\",\"city\":\"Falkenstein\"},"
            + "\"image\":{\"id\":9,\"type\":\"system\",\"name\":\"ubuntu-24.04\",\"description\":\"Ubuntu 24.04\",\"os_flavor\":\"ubuntu\"},"
            + "\"labels\":{\"nmox-ops\":\"true\"},\"volumes\":[]}";

    static String pagination(Object next) {
        return "\"meta\":{\"pagination\":{\"page\":1,\"per_page\":50,\"previous_page\":null,\"next_page\":" + next
                + ",\"last_page\":3,\"total_entries\":3}}";
    }

    static String server(int id, String name) {
        return SERVER.replace("4242", String.valueOf(id)).replace("nmox-a1", name);
    }

    static String error(String code, String message) {
        return "{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"details\":null}}";
    }

    /** Answers from a map; anything unmapped is Hetzner's documented 404. */
    static final class FakeApi implements CloudHttp.Transport {
        final Map<String, String> answers = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();

        FakeApi on(String call, String answer) {
            answers.put(call, answer);
            return this;
        }

        @Override
        public JSONObject send(String method, String path, String body) throws IOException {
            calls.add(method + " " + path);
            bodies.add(body);
            String a = answers.get(method + " " + path);
            if (a == null) {
                throw new IOException("HTTP 404: " + error("not_found", "server with ID '1' not found"));
            }
            return new JSONObject(a);
        }
    }

    static final String P = "?page=1&per_page=50";

    /** A catalogue of two locations, server types with per-location prices, images in both architectures, one key. */
    static FakeApi catalogue() {
        return new FakeApi()
                .on("GET /locations" + P, "{\"locations\":["
                        + "{\"id\":1,\"name\":\"fsn1\",\"description\":\"Falkenstein DC Park 1\",\"country\":\"DE\",\"city\":\"Falkenstein\",\"network_zone\":\"eu-central\"},"
                        + "{\"id\":4,\"name\":\"ash\",\"description\":\"Ashburn, VA\",\"country\":\"US\",\"city\":\"Ashburn, VA\",\"network_zone\":\"us-east\"}],"
                        + pagination("null") + "}")
                .on("GET /server_types" + P, "{\"server_types\":["
                        // dearer in Ashburn than in Falkenstein: one type, two prices
                        + "{\"id\":2,\"name\":\"cpx22\",\"cores\":2,\"memory\":4,\"disk\":80,\"deprecation\":null,"
                        + "\"prices\":[{\"location\":\"fsn1\",\"price_hourly\":{\"net\":\"0.0100\",\"gross\":\"0.0119\"},\"price_monthly\":{\"net\":\"6.4900\",\"gross\":\"7.7231\"}},"
                        + "{\"location\":\"ash\",\"price_hourly\":{\"net\":\"0.0150\",\"gross\":\"0.0150\"},\"price_monthly\":{\"net\":\"9.4900\",\"gross\":\"9.4900\"}}],"
                        + "\"locations\":[{\"id\":1,\"name\":\"fsn1\",\"deprecation\":null,\"recommended\":true,\"available\":true},{\"id\":4,\"name\":\"ash\",\"deprecation\":null,\"recommended\":false,\"available\":true}]},"
                        // the cheapest, listed second: the menu sorts by price
                        + "{\"id\":1,\"name\":\"cx22\",\"cores\":2,\"memory\":4,\"disk\":40,\"deprecation\":null,"
                        + "\"prices\":[{\"location\":\"fsn1\",\"price_hourly\":{\"net\":\"0.0053\",\"gross\":\"0.0063\"},\"price_monthly\":{\"net\":\"3.2900\",\"gross\":\"3.9151\"}},"
                        + "{\"location\":\"ash\",\"price_hourly\":{\"net\":\"0.0053\",\"gross\":\"0.0053\"},\"price_monthly\":{\"net\":\"3.2900\",\"gross\":\"3.2900\"}}],"
                        // priced in Ashburn but not supported there: the locations array is the authority
                        + "\"locations\":[{\"id\":1,\"name\":\"fsn1\",\"deprecation\":null,\"recommended\":true,\"available\":true}]},"
                        // deprecated as a whole
                        + "{\"id\":3,\"name\":\"cx11\",\"cores\":1,\"memory\":2,\"disk\":20,\"deprecation\":{\"announced\":\"2023-06-01T00:00:00Z\",\"unavailable_after\":\"2023-09-01T00:00:00Z\"},"
                        + "\"prices\":[{\"location\":\"fsn1\",\"price_hourly\":{\"net\":\"0.0010\",\"gross\":\"0.0010\"},\"price_monthly\":{\"net\":\"1.0000\",\"gross\":\"1.0000\"}}],"
                        + "\"locations\":[{\"id\":1,\"name\":\"fsn1\",\"deprecation\":null,\"available\":true}]},"
                        // temporarily unavailable where it is listed
                        + "{\"id\":5,\"name\":\"cax11\",\"cores\":2,\"memory\":4,\"disk\":40,\"deprecation\":null,"
                        + "\"prices\":[{\"location\":\"fsn1\",\"price_hourly\":{\"net\":\"0.0010\",\"gross\":\"0.0010\"},\"price_monthly\":{\"net\":\"2.0000\",\"gross\":\"2.0000\"}}],"
                        + "\"locations\":[{\"id\":1,\"name\":\"fsn1\",\"deprecation\":null,\"available\":false}]}],"
                        + pagination("null") + "}")
                .on("GET /images?type=system&status=available&page=1&per_page=50", "{\"images\":["
                        + "{\"id\":1,\"type\":\"system\",\"status\":\"available\",\"name\":\"ubuntu-24.04\",\"description\":\"Ubuntu 24.04\",\"os_flavor\":\"ubuntu\",\"os_version\":\"24.04\",\"deprecation\":null,\"architecture\":\"x86\"},"
                        + "{\"id\":2,\"type\":\"system\",\"status\":\"available\",\"name\":\"ubuntu-24.04\",\"description\":\"Ubuntu 24.04\",\"os_flavor\":\"ubuntu\",\"os_version\":\"24.04\",\"deprecation\":null,\"architecture\":\"arm\"},"
                        + "{\"id\":3,\"type\":\"system\",\"status\":\"available\",\"name\":\"fedora-41\",\"description\":\"Fedora 41\",\"os_flavor\":\"fedora\",\"os_version\":\"41\",\"deprecation\":null,\"architecture\":\"x86\"},"
                        + "{\"id\":4,\"type\":\"system\",\"status\":\"available\",\"name\":\"debian-12\",\"description\":\"Debian 12\",\"os_flavor\":\"debian\",\"os_version\":\"12\",\"deprecation\":null,\"architecture\":\"x86\"},"
                        + "{\"id\":5,\"type\":\"system\",\"status\":\"available\",\"name\":\"ubuntu-20.04\",\"description\":\"Ubuntu 20.04\",\"os_flavor\":\"ubuntu\",\"os_version\":\"20.04\","
                        + "\"deprecation\":{\"announced\":\"2025-04-01T00:00:00Z\",\"unavailable_after\":\"2025-07-01T00:00:00Z\"},\"architecture\":\"x86\"}],"
                        + pagination("null") + "}")
                .on("GET /ssh_keys" + P, "{\"ssh_keys\":[{\"id\":99,\"name\":\"laptop\",\"fingerprint\":\"aa:bb\",\"public_key\":\"ssh-ed25519 AAAA\",\"labels\":{}}],"
                        + pagination("null") + "}")
                .on("GET /pricing", "{\"pricing\":{\"currency\":\"EUR\",\"vat_rate\":\"19.00\",\"primary_ips\":["
                        + "{\"type\":\"ipv6\",\"prices\":[{\"location\":\"fsn1\",\"price_hourly\":{\"net\":\"0.0000\",\"gross\":\"0.0000\"},\"price_monthly\":{\"net\":\"0.0000\",\"gross\":\"0.0000\"}}]},"
                        + "{\"type\":\"ipv4\",\"prices\":[{\"location\":\"fsn1\",\"price_hourly\":{\"net\":\"0.0008\",\"gross\":\"0.0010\"},\"price_monthly\":{\"net\":\"0.5000\",\"gross\":\"0.5950\"}},"
                        + "{\"location\":\"ash\",\"price_hourly\":{\"net\":\"0.0008\",\"gross\":\"0.0008\"},\"price_monthly\":{\"net\":\"0.5000\",\"gross\":\"0.5000\"}}]}]}}");
    }

    @Test
    @DisplayName("locations are the regions, a server type is a size once per location it can be had in, Ubuntu/Debian images once per name, the account's keys")
    void options() throws Exception {
        FakeApi api = catalogue();
        ProviderOptions o = new HetznerOps(api).options();
        assertThat(o.regions()).extracting(ProviderOptions.Region::slug).containsExactly("fsn1", "ash");
        assertThat(o.regions().get(0).name()).isEqualTo("Falkenstein, DE");
        assertThat(o.regions()).allMatch(ProviderOptions.Region::available);
        // cheapest first; cx22 not in ash (not supported there), cx11 deprecated, cax11 unavailable
        assertThat(o.sizes()).extracting(s -> s.slug() + "@" + s.regions().get(0))
                .containsExactly("cx22@fsn1", "cpx22@fsn1", "cpx22@ash");
        ProviderOptions.Size cx22 = o.sizes().get(0);
        assertThat(cx22.vcpus()).isEqualTo(2);
        assertThat(cx22.memoryMb()).as("the API states memory in GB").isEqualTo(4096);
        assertThat(cx22.diskGb()).isEqualTo(40);
        assertThat(o.images()).extracting(ProviderOptions.Image::slug).containsExactly("ubuntu-24.04", "debian-12");
        assertThat(o.images().get(0).distribution()).isEqualTo("Ubuntu");
        assertThat(o.images().get(0).name()).isEqualTo("24.04");
        assertThat(o.sshKeys()).extracting(ProviderOptions.SshKey::id).containsExactly("99");
        assertThat(api.calls).as("the catalogue is read, nothing is written").allMatch(c -> c.startsWith("GET "));
    }

    @Test
    @DisplayName("a price is the gross decimal STRING, read exactly; the bill is the server type plus the public IPv4 at that location, in the currency the price list names")
    void pricesComeFromTheStrings() throws Exception {
        ProviderOptions o = new HetznerOps(catalogue()).options();
        assertThat(o.currency()).isEqualTo("EUR");
        assertThat(o.currencySign()).isEqualTo("€");
        // cx22 at fsn1: 3.9151 gross (NOT 3.29 net) + 0.5950 for the address
        assertThat(o.sizes().get(0).priceMonthly()).isCloseTo(4.5101, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(o.sizes().get(0).priceHourly()).isCloseTo(0.0073, org.assertj.core.data.Offset.offset(1e-9));
        // cpx22 differs by location: 7.7231 + 0.5950 in fsn1, 9.49 + 0.50 in ash
        assertThat(o.sizes().get(1).priceMonthly()).isCloseTo(8.3181, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(o.sizes().get(2).priceMonthly()).isCloseTo(9.99, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(HetznerOps.gross(new JSONObject("{\"net\":\"1.0000\",\"gross\":\"1.1900\"}"))).isEqualTo(1.19);
        assertThat(HetznerOps.gross(new JSONObject("{\"net\":1,\"gross\":1.19}"))).as("a number is read too").isEqualTo(1.19);
        assertThat(HetznerOps.gross(new JSONObject("{\"net\":\"1.0000\"}"))).isNaN();
        assertThat(HetznerOps.gross(new JSONObject("{\"gross\":\"free\"}"))).isNaN();
        assertThat(HetznerOps.gross(new JSONObject("{\"gross\":\"-1\"}"))).isNaN();
        assertThat(HetznerOps.gross(null)).isNaN();
    }

    @Test
    @DisplayName("a size whose price cannot be read is not offered, and a price list with no currency refuses the whole menu")
    void unpricedIsNotOffered() throws Exception {
        FakeApi api = catalogue();
        api.on("GET /server_types" + P, "{\"server_types\":[{\"id\":1,\"name\":\"cx22\",\"cores\":2,\"memory\":4,\"disk\":40,\"deprecation\":null,"
                + "\"prices\":[{\"location\":\"fsn1\",\"price_hourly\":{\"gross\":\"0.01\"},\"price_monthly\":{\"gross\":\"soon\"}}]}]," + pagination("null") + "}");
        assertThat(new HetznerOps(api).options().sizes()).isEmpty();
        api.on("GET /pricing", "{\"pricing\":{\"vat_rate\":\"19.00\"}}");
        assertThatThrownBy(() -> new HetznerOps(api).options()).isInstanceOf(IOException.class).hasMessageContaining("no currency");
    }

    @Test
    @DisplayName("the catalogue is paged too: a server type on a second page is a size")
    void catalogueFollowsPages() throws Exception {
        FakeApi api = catalogue();
        String type = "{\"id\":%d,\"name\":\"%s\",\"cores\":2,\"memory\":4,\"disk\":40,\"deprecation\":null,"
                + "\"prices\":[{\"location\":\"fsn1\",\"price_hourly\":{\"gross\":\"0.01\"},\"price_monthly\":{\"gross\":\"%s\"}}]}";
        api.on("GET /server_types" + P, "{\"server_types\":[" + type.formatted(1, "one", "1.00") + "]," + pagination(2) + "}");
        api.on("GET /server_types?page=2&per_page=50", "{\"server_types\":[" + type.formatted(2, "two", "2.00") + "]," + pagination("null") + "}");
        assertThat(new HetznerOps(api).options().sizes()).extracting(ProviderOptions.Size::slug).containsExactly("one", "two");
    }

    @Test
    @DisplayName("a create names the server type, image and location, carries the keys as numbers, the nmox-ops LABEL, the public IPv4 and the cloud-init — and no tag, no renamed host")
    void createBody() throws Exception {
        FakeApi api = new FakeApi().on("POST /servers", "{\"server\":" + SERVER.replace("\"running\"", "\"initializing\"")
                .replace("{\"id\":7,\"ip\":\"203.0.113.9\",\"blocked\":false,\"dns_ptr\":\"static.example\"}", "null")
                + ",\"action\":{\"id\":1,\"command\":\"create_server\",\"status\":\"running\"},\"next_actions\":[],\"root_password\":null}");
        HostSpec spec = new HostSpec("nmox-a1", "fsn1", "cx22", "ubuntu-24.04", List.of("99", "by-name"), CloudInit.USER_DATA);
        HostRecord r = new HetznerOps(api).createHost(spec);
        assertThat(api.calls).containsExactly("POST /servers");
        JSONObject body = new JSONObject(api.bodies.get(0));
        assertThat(body.getString("name")).as("the name is the name: the Hub's mark is a label, never a prefix").isEqualTo("nmox-a1");
        assertThat(body.getString("server_type")).isEqualTo("cx22");
        assertThat(body.getString("image")).isEqualTo("ubuntu-24.04");
        assertThat(body.getString("location")).isEqualTo("fsn1");
        assertThat(body.getJSONObject("labels").toMap()).isEqualTo(Map.of("nmox-ops", "true"));
        assertThat(body.has("tags")).as("Hetzner has labels, not tags").isFalse();
        assertThat(body.getJSONArray("ssh_keys").get(0)).as("an id travels as an integer").isInstanceOf(Number.class);
        assertThat(body.getJSONArray("ssh_keys").getLong(0)).isEqualTo(99L);
        assertThat(body.getJSONArray("ssh_keys").get(1)).as("a name travels as a string").isEqualTo("by-name");
        assertThat(body.getJSONObject("public_net").getBoolean("enable_ipv4")).isTrue();
        assertThat(body.getBoolean("start_after_create")).isTrue();
        assertThat(body.getString("user_data")).startsWith("#cloud-config").contains("docker.io").contains("mosh");
        assertThat(r.providerId()).isEqualTo("4242");
        assertThat(r.phase()).isEqualTo(HostRecord.Phase.COMING_UP);
        assertThat(r.active()).isFalse();
        assertThat(r.address()).as("no address yet: ipv4 is null until one is assigned").isNull();
        assertThat(r.providerStatus()).isEqualTo("initializing");
        assertThat(r.facts()).containsEntry("made here", "yes");
    }

    @Test
    @DisplayName("an answer to a create that holds no server is refused without quoting it: such an answer can carry a root password")
    void createAnswerIsNeverQuoted() {
        FakeApi api = new FakeApi().on("POST /servers", "{\"root_password\":\"YItygq1v3GYjjMomLaKc\",\"action\":{\"id\":1}}");
        HostSpec spec = new HostSpec("nmox-a1", "fsn1", "cx22", "ubuntu-24.04", List.of(), "");
        assertThatThrownBy(() -> new HetznerOps(api).createHost(spec)).isInstanceOf(IOException.class)
                .satisfies(ex -> assertThat(String.valueOf(ex.getMessage()) + ex).doesNotContain("YItygq1v3GYjjMomLaKc"));
    }

    @Test
    @DisplayName("a server parses with its public IPv4, its facts and whether it was made here — by the label, whatever its value")
    void parseServer() {
        HostRecord r = HetznerOps.parseServer(new JSONObject(SERVER));
        assertThat(r.providerId()).isEqualTo("4242");
        assertThat(r.name()).isEqualTo("nmox-a1");
        assertThat(r.address()).isEqualTo("203.0.113.9");
        assertThat(r.active()).isTrue();
        assertThat(r.facts()).containsEntry("region", "Falkenstein (fsn1)").containsEntry("size", "cx22")
                .containsEntry("image", "Ubuntu 24.04").containsEntry("created", "2026-11-06T10:00:00Z")
                .containsEntry("made here", "yes");
        assertThat(HetznerOps.parseServer(new JSONObject(SERVER.replace("{\"nmox-ops\":\"true\"}", "{\"nmox-ops\":\"\"}"))).facts())
                .containsEntry("made here", "yes");
        assertThat(HetznerOps.parseServer(new JSONObject(SERVER.replace("{\"nmox-ops\":\"true\"}", "{\"env\":\"prod\"}"))).facts())
                .as("a server made in Hetzner's console is listed, and is not ours").containsEntry("made here", "no");
        // a server named like one of ours, without the label, is not ours: the mark is the label
        assertThat(HetznerOps.parseServer(new JSONObject(SERVER.replace("{\"nmox-ops\":\"true\"}", "{}")
                .replace("nmox-a1", "nmox-ops-a1"))).facts()).containsEntry("made here", "no");
    }

    @ParameterizedTest(name = "{0} is {1}")
    @DisplayName("Hetzner's status is the lifecycle: running runs, initializing and starting are on their way, everything else is not running — an unknown word included")
    @CsvSource({"running,RUNNING", "initializing,COMING_UP", "starting,COMING_UP", "stopping,NOT_RUNNING", "off,NOT_RUNNING",
            "deleting,NOT_RUNNING", "migrating,NOT_RUNNING", "rebuilding,NOT_RUNNING", "unknown,NOT_RUNNING",
            "hibernating,NOT_RUNNING", "'',NOT_RUNNING", "Running,NOT_RUNNING"})
    void statusMapping(String status, HostRecord.Phase phase) {
        assertThat(HetznerOps.phase(status)).isEqualTo(phase);
        HostRecord r = HetznerOps.parseServer(new JSONObject(SERVER.replace("\"status\":\"running\"", "\"status\":\"" + status + "\"")));
        assertThat(r.phase()).isEqualTo(phase);
        assertThat(r.providerStatus()).as("Hetzner's own word travels for the reason").isEqualTo(status);
    }

    @Test
    @DisplayName("the listing reads every page: three pages are three requests, the third's null next_page ends it, and no label narrows it")
    void listingFollowsPages() throws Exception {
        FakeApi api = new FakeApi()
                .on("GET /servers?page=1&per_page=50", "{\"servers\":[" + server(4242, "nmox-a1") + "]," + pagination(2) + "}")
                .on("GET /servers?page=2&per_page=50", "{\"servers\":[" + server(4343, "nmox-b2") + "]," + pagination(3) + "}")
                .on("GET /servers?page=3&per_page=50", "{\"servers\":[" + server(4444, "made-in-the-console") + "]," + pagination("null") + "}");
        List<HostRecord> listed = new HetznerOps(api).listHosts();
        assertThat(listed).extracting(HostRecord::providerId).containsExactly("4242", "4343", "4444");
        assertThat(api.calls).containsExactly("GET /servers?page=1&per_page=50", "GET /servers?page=2&per_page=50",
                "GET /servers?page=3&per_page=50");
        assertThat(api.calls).as("every server of the project is listed, as DigitalOcean's are").noneMatch(c -> c.contains("label_selector"));
        FakeApi one = new FakeApi().on("GET /servers?page=1&per_page=50", "{\"servers\":[" + SERVER + "]," + pagination("null") + "}");
        assertThat(new HetznerOps(one).listHosts()).hasSize(1);
        assertThat(one.calls).as("a null next_page, no second request").hasSize(1);
    }

    @Test
    @DisplayName("the next page is the one the API names, not the one after this one")
    void listingGoesWhereItIsSent() throws Exception {
        FakeApi api = new FakeApi()
                .on("GET /servers?page=1&per_page=50", "{\"servers\":[" + server(1, "a") + "]," + pagination(7) + "}")
                .on("GET /servers?page=7&per_page=50", "{\"servers\":[" + server(2, "b") + "]," + pagination("null") + "}");
        assertThat(new HetznerOps(api).listHosts()).extracting(HostRecord::name).containsExactly("a", "b");
    }

    @Test
    @DisplayName("a listing that cannot be read whole is refused whole: past the cap, a page that points back, a full page with no pagination, a failing second page")
    void partialListingsAreRefused() {
        CloudHttp.Transport endless = (method, path, body) -> {
            int page = Integer.parseInt(path.replaceAll(".*[?&]page=(\\d+).*", "$1"));
            return new JSONObject("{\"servers\":[" + server(page, "s" + page) + "]," + pagination(page + 1) + "}");
        };
        assertThatThrownBy(() -> new HetznerOps(endless).listHosts())
                .isInstanceOf(IOException.class).hasMessageContaining("more than 2000 servers");
        CloudHttp.Transport circular = (method, path, body) -> new JSONObject("{\"servers\":[" + SERVER + "]," + pagination(1) + "}");
        assertThatThrownBy(() -> new HetznerOps(circular).listHosts())
                .isInstanceOf(IOException.class).hasMessageContaining("named page 1");
        StringBuilder full = new StringBuilder();
        for (int i = 0; i < HetznerOps.PER_PAGE; i++) {
            full.append(i == 0 ? "" : ",").append(server(100 + i, "s" + i));
        }
        CloudHttp.Transport silent = (method, path, body) -> new JSONObject("{\"servers\":[" + full + "]}");
        assertThatThrownBy(() -> new HetznerOps(silent).listHosts())
                .isInstanceOf(IOException.class).hasMessageContaining("without saying whether another follows");
        FakeApi broken = new FakeApi().on("GET /servers?page=1&per_page=50", "{\"servers\":[" + SERVER + "]," + pagination(2) + "}");
        assertThatThrownBy(() -> new HetznerOps(broken).listHosts()).as("page two fails: no list of page one alone")
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("reading a server the API answers 404 for is null — gone; a 500, a 200 without a server and a missing token are raised — unknown")
    void readHost() throws Exception {
        FakeApi api = new FakeApi().on("GET /servers/4242", "{\"server\":" + SERVER + "}");
        HetznerOps ops = new HetznerOps(api);
        assertThat(ops.readHost("4242")).isNotNull();
        assertThat(ops.readHost("1")).as("Hetzner's not_found").isNull();
        CloudHttp.Transport broken = (method, path, body) -> {
            throw new IOException("HTTP 500: " + error("server_error", "something went wrong"));
        };
        assertThatThrownBy(() -> new HetznerOps(broken).readHost("4242")).isInstanceOf(IOException.class)
                .hasMessageContaining("500").hasMessageContaining("something went wrong");
        CloudHttp.Transport limited = (method, path, body) -> {
            throw new IOException("HTTP 429: " + error("rate_limit_exceeded", "limit of 3600 requests per hour reached"));
        };
        assertThatThrownBy(() -> new HetznerOps(limited).readHost("4242")).hasMessageContaining("429");
        CloudHttp.Transport empty = (method, path, body) -> new JSONObject("{}");
        assertThatThrownBy(() -> new HetznerOps(empty).readHost("4242")).as("an answer without a server is not 'gone'")
                .isInstanceOf(IOException.class).hasMessageContaining("without a server");
        CloudHttp.Transport tokenless = (method, path, body) -> {
            throw new IOException("no Hetzner Cloud API token configured");
        };
        assertThatThrownBy(() -> new HetznerOps(tokenless).readHost("4242")).hasMessage("no Hetzner Cloud API token configured");
        // a body that merely mentions 404 is not the API's 404
        CloudHttp.Transport impostor = (method, path, body) -> {
            throw new IOException("HTTP 503: " + error("unavailable", "upstream said HTTP 404: not found"));
        };
        assertThatThrownBy(() -> new HetznerOps(impostor).readHost("4242")).hasMessageContaining("503");
    }

    @Test
    @DisplayName("only a number is spliced into a path: an id that is not one is refused before any request")
    void idsAreNumbers() {
        FakeApi api = new FakeApi();
        HetznerOps ops = new HetznerOps(api);
        assertThatThrownBy(() -> ops.readHost("42/actions/poweroff")).isInstanceOf(IOException.class).hasMessageContaining("not a Hetzner Cloud server id");
        assertThatThrownBy(() -> ops.destroyHost("../ssh_keys/1")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ops.destroyHost(null)).isInstanceOf(IOException.class);
        assertThat(api.calls).isEmpty();
    }

    @Test
    @DisplayName("destroy is one DELETE of that server; a failed destroy is raised with Hetzner's reason")
    void destroy() throws Exception {
        FakeApi api = new FakeApi().on("DELETE /servers/4242", "{\"action\":{\"id\":3,\"command\":\"delete_server\",\"status\":\"running\"}}");
        new HetznerOps(api).destroyHost("4242");
        assertThat(api.calls).containsExactly("DELETE /servers/4242");
        CloudHttp.Transport locked = (method, path, body) -> {
            throw new IOException("HTTP 423: " + error("protected", "server deletion is protected"));
        };
        assertThatThrownBy(() -> new HetznerOps(locked).destroyHost("4242"))
                .hasMessage("HTTP 423: server deletion is protected (protected)");
    }

    @Test
    @DisplayName("a key upload is one POST of the trimmed public key, labeled as ours, and answers the id; the server that follows carries that id")
    void keyUploadThenCreate() throws Exception {
        FakeApi api = new FakeApi()
                .on("POST /ssh_keys", "{\"ssh_key\":{\"id\":77,\"name\":\"NMOX Studio id_ed25519.pub\",\"fingerprint\":\"aa:bb\",\"public_key\":\"ssh-ed25519 AAAA x\",\"labels\":{\"nmox-ops\":\"true\"}}}")
                .on("POST /servers", "{\"server\":" + SERVER + ",\"action\":{\"id\":1},\"root_password\":null}");
        HetznerOps ops = new HetznerOps(api);
        String id = ops.addSshKey("NMOX Studio id_ed25519.pub", "ssh-ed25519 AAAA x\n");
        assertThat(id).isEqualTo("77");
        JSONObject key = new JSONObject(api.bodies.get(0));
        assertThat(key.getString("name")).isEqualTo("NMOX Studio id_ed25519.pub");
        assertThat(key.getString("public_key")).isEqualTo("ssh-ed25519 AAAA x");
        assertThat(key.getJSONObject("labels").has("nmox-ops")).isTrue();
        ops.createHost(new HostSpec("nmox-a1", "fsn1", "cx22", "ubuntu-24.04", List.of(id), ""));
        assertThat(api.calls).containsExactly("POST /ssh_keys", "POST /servers");
        assertThat(new JSONObject(api.bodies.get(1)).getJSONArray("ssh_keys").getLong(0)).isEqualTo(77L);
        assertThat(new JSONObject(api.bodies.get(1)).has("user_data")).as("no cloud-init asked, none sent").isFalse();
    }

    @Test
    @DisplayName("neither the catalogue nor a create uploads a key: only the upload does, and the caller makes it after the priced yes")
    void nothingElseUploadsAKey() throws Exception {
        FakeApi api = catalogue().on("POST /servers", "{\"server\":" + SERVER + "}");
        HetznerOps ops = new HetznerOps(api);
        ops.options();
        ops.createHost(new HostSpec("nmox-a1", "fsn1", "cx22", "ubuntu-24.04", List.of("99"), ""));
        assertThat(api.calls).noneMatch(c -> c.contains("/ssh_keys") && c.startsWith("POST"));
        assertThat(api.calls.stream().filter(c -> c.startsWith("POST"))).containsExactly("POST /servers");
    }

    @Test
    @DisplayName("a duplicate key is refused in Hetzner's own words, with the status in front and the code behind")
    void refusalsAreSentences() {
        CloudHttp.Transport taken = (method, path, body) -> {
            throw new IOException("HTTP 409: " + error("uniqueness_error", "SSH key with the same fingerprint already exists"));
        };
        assertThatThrownBy(() -> new HetznerOps(taken).addSshKey("n", "ssh-ed25519 AAAA"))
                .hasMessage("HTTP 409: SSH key with the same fingerprint already exists (uniqueness_error)");
        // the transport cuts a body at 160 characters: a cut one is still read
        String cut = "HTTP 422: {\"error\":{\"code\":\"invalid_input\",\"message\":\"invalid input in field 'name': is not a valid \\\"hostname\\\"\",\"details\":{\"fields\":[{\"name\":\"na…";
        assertThat(HetznerOps.readable(new IOException(cut)).getMessage())
                .isEqualTo("HTTP 422: invalid input in field 'name': is not a valid \"hostname\" (invalid_input)");
        assertThat(HetznerOps.readable(new IOException("HTTP 502: <html>Bad Gateway</html>")).getMessage())
                .as("not Hetzner's shape: as it came").isEqualTo("HTTP 502: <html>Bad Gateway</html>");
        assertThat(HetznerOps.readable(new IOException("connection reset")).getMessage()).isEqualTo("connection reset");
        assertThat(HetznerOps.readable(new IOException("HTTP 404: " + error("not_found", "server with ID '9' not found"))).getMessage())
                .as("the head that tells a 404 from everything else is kept").startsWith("HTTP 404:");
    }

    @Test
    @DisplayName("it is found the way every provider is, under its own id and Hetzner's name, and asks the keychain entry the Infra Designer keeps")
    void registration() throws Exception {
        HetznerOps ops = new HetznerOps(new FakeApi());
        assertThat(ops.id()).isEqualTo("hetzner");
        assertThat(ops.displayName()).isEqualTo("Hetzner Cloud");
        assertThat(ops.simulated()).isFalse();
        assertThat(HetznerOps.LABEL).isEqualTo(DigitalOceanOps.TAG).isEqualTo("nmox-ops");
        assertThat(org.openide.util.Lookup.getDefault().lookupAll(OpsProvider.class))
                .as("registered for the default Lookup, beside DigitalOcean")
                .anyMatch(HetznerOps.class::isInstance).anyMatch(DigitalOceanOps.class::isInstance);
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/infra/ops/provider/HetznerOps.java"));
        assertThat(source).as("the token is the Infra Designer's Hetzner token, from the keychain store")
                .contains("CloudHttp.over(CloudProvider.HETZNER)").contains("CloudProvider.HETZNER.hasToken()")
                .doesNotContain("Preferences").doesNotContain("System.getenv").doesNotContain("Files.");
    }
}

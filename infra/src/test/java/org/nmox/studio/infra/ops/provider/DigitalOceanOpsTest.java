package org.nmox.studio.infra.ops.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.api.CloudHttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The DigitalOcean provider against a transport that answers from a map — no token, no bill. */
class DigitalOceanOpsTest {

    static final String DROPLET = "{\"id\":4242,\"name\":\"nmox-a1\",\"status\":\"active\",\"created_at\":\"2026-11-06T10:00:00Z\","
            + "\"size_slug\":\"s-1vcpu-1gb\",\"region\":{\"slug\":\"nyc3\",\"name\":\"New York 3\"},\"image\":{\"slug\":\"ubuntu-24-04-x64\",\"name\":\"24.04 (LTS) x64\"},"
            + "\"networks\":{\"v4\":[{\"ip_address\":\"10.0.0.2\",\"type\":\"private\"},{\"ip_address\":\"203.0.113.9\",\"type\":\"public\"}]},"
            + "\"tags\":[\"nmox-ops\"],\"vpc_uuid\":\"vpc-1\"}";

    static final class FakeApi implements CloudHttp.Transport {
        final Map<String, String> answers;
        final List<String> calls = new ArrayList<>();
        String lastBody;

        FakeApi(Map<String, String> answers) {
            this.answers = answers;
        }

        @Override
        public JSONObject send(String method, String path, String body) throws IOException {
            calls.add(method + " " + path);
            lastBody = body;
            String a = answers.get(method + " " + path);
            if (a == null) {
                throw new IOException("HTTP 404: {\"id\":\"not_found\"}");
            }
            return new JSONObject(a);
        }
    }

    @Test
    @DisplayName("the account's regions, sizes (cheapest first, unavailable dropped), Ubuntu/Debian images and keys parse")
    void options() throws Exception {
        FakeApi api = new FakeApi(Map.of(
                "GET /v2/regions?per_page=200", "{\"regions\":[{\"slug\":\"nyc3\",\"name\":\"New York 3\",\"available\":true},{\"slug\":\"sfo1\",\"name\":\"Old\",\"available\":false}]}",
                "GET /v2/sizes?per_page=200", "{\"sizes\":[{\"slug\":\"s-2vcpu-2gb\",\"memory\":2048,\"vcpus\":2,\"disk\":60,\"price_monthly\":18,\"price_hourly\":0.02679,\"regions\":[\"nyc3\"],\"available\":true},"
                        + "{\"slug\":\"s-1vcpu-1gb\",\"memory\":1024,\"vcpus\":1,\"disk\":25,\"price_monthly\":6,\"price_hourly\":0.00893,\"regions\":[\"nyc3\"],\"available\":true},"
                        + "{\"slug\":\"gone\",\"memory\":1024,\"vcpus\":1,\"disk\":25,\"price_monthly\":1,\"price_hourly\":0.001,\"regions\":[],\"available\":false}]}",
                "GET /v2/images?type=distribution&per_page=200", "{\"images\":[{\"id\":1,\"slug\":\"ubuntu-24-04-x64\",\"name\":\"24.04 (LTS) x64\",\"distribution\":\"Ubuntu\"},"
                        + "{\"id\":2,\"slug\":\"fedora-40-x64\",\"name\":\"40 x64\",\"distribution\":\"Fedora\"},{\"id\":3,\"slug\":\"debian-12-x64\",\"name\":\"12 x64\",\"distribution\":\"Debian\"}]}",
                "GET /v2/account/keys?per_page=200", "{\"ssh_keys\":[{\"id\":99,\"name\":\"laptop\",\"fingerprint\":\"aa:bb\"}]}"));
        ProviderOptions o = new DigitalOceanOps(api).options();
        assertThat(o.regions()).extracting(ProviderOptions.Region::slug).containsExactly("nyc3", "sfo1");
        assertThat(o.sizes()).extracting(ProviderOptions.Size::slug).containsExactly("s-1vcpu-1gb", "s-2vcpu-2gb");
        assertThat(o.sizes().get(0).priceMonthly()).isEqualTo(6.0);
        assertThat(o.images()).extracting(ProviderOptions.Image::slug).containsExactly("ubuntu-24-04-x64", "debian-12-x64");
        assertThat(o.sshKeys()).extracting(ProviderOptions.SshKey::id).containsExactly("99");
    }

    @Test
    @DisplayName("a create carries the name, the keys as numbers, the nmox-ops tag, monitoring and the cloud-init")
    void createBody() throws Exception {
        FakeApi api = new FakeApi(Map.of("POST /v2/droplets", "{\"droplet\":" + DROPLET.replace("\"active\"", "\"new\"") + "}"));
        HostSpec spec = new HostSpec("nmox-a1", "nyc3", "s-1vcpu-1gb", "ubuntu-24-04-x64", List.of("99"), CloudInit.USER_DATA);
        HostRecord r = new DigitalOceanOps(api).createHost(spec);
        JSONObject body = new JSONObject(api.lastBody);
        assertThat(body.getString("name")).isEqualTo("nmox-a1");
        assertThat(body.getJSONArray("ssh_keys").getLong(0)).isEqualTo(99L);
        assertThat(body.getJSONArray("tags").getString(0)).isEqualTo("nmox-ops");
        assertThat(body.getBoolean("monitoring")).isTrue();
        assertThat(body.getString("user_data")).startsWith("#cloud-config").contains("docker.io").contains("mosh");
        assertThat(r.providerId()).isEqualTo("4242");
        assertThat(r.active()).isFalse();
        assertThat(r.providerStatus()).isEqualTo("new");
    }

    @Test
    @DisplayName("a droplet parses with its public address, its facts and whether it was made here")
    void parseDroplet() {
        HostRecord r = DigitalOceanOps.parseDroplet(new JSONObject(DROPLET));
        assertThat(r.address()).isEqualTo("203.0.113.9");
        assertThat(r.active()).isTrue();
        assertThat(r.name()).isEqualTo("nmox-a1");
        assertThat(r.facts()).containsEntry("region", "New York 3").containsEntry("size", "s-1vcpu-1gb")
                .containsEntry("made here", "yes").containsEntry("vpc", "vpc-1");
        HostRecord other = DigitalOceanOps.parseDroplet(new JSONObject(DROPLET.replace("[\"nmox-ops\"]", "[]")));
        assertThat(other.facts()).containsEntry("made here", "no");
    }

    @Test
    @DisplayName("reading a droplet the API no longer has answers null; any other failure is raised")
    void readHost() throws Exception {
        FakeApi api = new FakeApi(Map.of("GET /v2/droplets/4242", "{\"droplet\":" + DROPLET + "}"));
        DigitalOceanOps ops = new DigitalOceanOps(api);
        assertThat(ops.readHost("4242")).isNotNull();
        assertThat(ops.readHost("1")).isNull();
        CloudHttp.Transport broken = (method, path, body) -> {
            throw new IOException("HTTP 500: boom");
        };
        assertThatThrownBy(() -> new DigitalOceanOps(broken).readHost("4242")).hasMessageContaining("500");
    }

    @Test
    @DisplayName("destroy is one DELETE; a key upload posts the public key and returns its id")
    void destroyAndKey() throws Exception {
        FakeApi api = new FakeApi(Map.of("DELETE /v2/droplets/4242", "{}",
                "POST /v2/account/keys", "{\"ssh_key\":{\"id\":77,\"name\":\"n\"}}"));
        DigitalOceanOps ops = new DigitalOceanOps(api);
        ops.destroyHost("4242");
        assertThat(ops.addSshKey("NMOX Studio", "ssh-ed25519 AAAA x\n")).isEqualTo("77");
        assertThat(new JSONObject(api.lastBody).getString("public_key")).isEqualTo("ssh-ed25519 AAAA x");
        assertThat(api.calls).containsExactly("DELETE /v2/droplets/4242", "POST /v2/account/keys");
    }

    @Test
    @DisplayName("the listing reads every page: a second page's servers are servers too")
    void listingFollowsPages() throws Exception {
        String second = DROPLET.replace("4242", "4343").replace("nmox-a1", "nmox-b2");
        FakeApi api = new FakeApi(Map.of(
                "GET /v2/droplets?page=1&per_page=100", "{\"droplets\":[" + DROPLET + "],\"links\":{\"pages\":{\"next\":\"https://api.digitalocean.com/v2/droplets?page=2&per_page=100\"}}}",
                "GET /v2/droplets?page=2&per_page=100", "{\"droplets\":[" + second + "],\"links\":{\"pages\":{}}}"));
        List<HostRecord> listed = new DigitalOceanOps(api).listHosts();
        assertThat(listed).extracting(HostRecord::providerId).containsExactly("4242", "4343");
        assertThat(api.calls).containsExactly("GET /v2/droplets?page=1&per_page=100", "GET /v2/droplets?page=2&per_page=100");
        FakeApi one = new FakeApi(Map.of("GET /v2/droplets?page=1&per_page=100", "{\"droplets\":[" + DROPLET + "],\"links\":{}}"));
        assertThat(new DigitalOceanOps(one).listHosts()).hasSize(1);
        assertThat(one.calls).as("no next link, no second request").hasSize(1);
    }

    @Test
    @DisplayName("an account past the cap is refused whole: a partial listing would report real servers as gone")
    void listingPastTheCapIsRefused() {
        CloudHttp.Transport endless = (method, path, body) -> new JSONObject(
                "{\"droplets\":[" + DROPLET + "],\"links\":{\"pages\":{\"next\":\"more\"}}}");
        assertThatThrownBy(() -> new DigitalOceanOps(endless).listHosts())
                .isInstanceOf(IOException.class).hasMessageContaining("more than 2000 servers");
    }

    @Test
    @DisplayName("an error from the cloud never carries the token")
    void tokenNeverInErrors() {
        assertThatThrownBy(() -> CloudHttp.send("http://127.0.0.1:9", "dop_v1_secret", "GET", "/x", null))
                .isInstanceOf(IOException.class)
                .satisfies(ex -> assertThat(String.valueOf(ex.getMessage()) + ex).doesNotContain("dop_v1_secret"));
    }
}

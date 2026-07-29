package org.nmox.studio.infra.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The DigitalOcean client's offline behavior — everything that runs
 * BEFORE a byte leaves the machine: plan execution over placeholder
 * resolution, the per-provider sweep's failure isolation, the honest
 * no-token refusals, drift/destroy statuses for kinds without a live
 * API path, and the EDT-marshalling seam (ledger 53d). Paths that
 * require a live cloud response (droplet IP polling, a real HTTP 404
 * driving the drifted verdict) are deliberately not simulated: the
 * client has no injectable transport, by design (see
 * DigitalOceanReadCapTest), so those lines are covered only by the
 * real-deploy gauntlets.
 *
 * <p>Every test that could otherwise reach {@code send()} first forces
 * the keyring fallback and skips itself if a token IS resolvable
 * (env var / legacy pref on a developer machine) — the suite must
 * never fire a genuine API call.
 */
class DigitalOceanClientSeamsTest {

    @BeforeEach
    void keepOffTheRealKeychain() {
        CloudTokens.keyringUsable = false;
    }

    private static void assumeNoToken(CloudProvider provider) {
        assumeTrue(!provider.hasToken(),
                "a real " + provider.displayName() + " token is configured — "
                + "skipping so no live API call can fire");
    }

    // ---- execute: skips, placeholder resolution, honest failure ----

    @Test
    @DisplayName("execute reports skipped steps, resolves known ids, and fails honestly on unknown ones")
    void executeSkipsResolvesAndFails() {
        InfraGraph graph = new InfraGraph();
        InfraNode a = graph.addNode(NodeKind.DROPLET, 0, 0);
        a.doId = "77";                       // pre-known id seeds the resolver
        InfraNode b = graph.addNode(NodeKind.SPACES, 0, 100);

        DoRequest skip = DoRequest.skip(b.id, "Spaces are S3-protocol");
        // body's placeholder resolves (a has an id); the path's cannot
        DoRequest doomed = new DoRequest("POST",
                "/v2/things/${id-of:ghost}",
                new JSONObject().put("vpc", "${id-of:" + a.id + "}"),
                a.id, "create thing", false);

        List<String> log = new ArrayList<>();
        boolean ok = new DigitalOceanClient().execute(List.of(skip, doomed), graph,
                (node, msg) -> log.add(msg));

        assertThat(ok).isFalse();
        assertThat(log).contains("skipped: Spaces are S3-protocol", "creating…");
        assertThat(log.get(log.size() - 1))
                .startsWith("FAILED:").contains("dependency ghost has no id yet");
    }

    // ---- sweep: isolation, interruption, compact messages ----

    @Test
    @DisplayName("sweep isolates failures per provider and announces each start")
    void sweepIsolatesFailures() {
        List<CloudProvider> started = new ArrayList<>();
        var outcomes = DigitalOceanClient.sweep(
                List.of(CloudProvider.DIGITALOCEAN, CloudProvider.HETZNER),
                new InfraGraph(), started::add,
                (provider, graph) -> {
                    if (provider == CloudProvider.DIGITALOCEAN) {
                        throw new IllegalStateException((String) null); // compact(null)
                    }
                    return 3;
                });

        assertThat(started).containsExactly(CloudProvider.DIGITALOCEAN, CloudProvider.HETZNER);
        assertThat(outcomes.get(CloudProvider.DIGITALOCEAN).failed()).isTrue();
        assertThat(outcomes.get(CloudProvider.DIGITALOCEAN).error()).isEqualTo("unknown error");
        assertThat(outcomes.get(CloudProvider.HETZNER).imported()).isEqualTo(3);
    }

    @Test
    @DisplayName("sweep compacts a long multi-line failure message to one bounded line")
    void sweepCompactsLongMessages() {
        String longMsg = ("boom\n" + "x".repeat(200));
        var outcomes = DigitalOceanClient.sweep(
                List.of(CloudProvider.CLOUDFLARE), new InfraGraph(), null,
                (provider, graph) -> {
                    throw new IOException(longMsg);
                });

        String error = outcomes.get(CloudProvider.CLOUDFLARE).error();
        assertThat(error).doesNotContain("\n").endsWith("…");
        assertThat(error.length()).isLessThanOrEqualTo(161);
    }

    @Test
    @DisplayName("an interrupted sweep stops, records 'interrupted', and restores the flag")
    void sweepInterruptedStops() {
        var outcomes = DigitalOceanClient.sweep(
                List.of(CloudProvider.DIGITALOCEAN, CloudProvider.HETZNER),
                new InfraGraph(), null,
                (provider, graph) -> {
                    throw new InterruptedException("stop");
                });

        assertThat(Thread.interrupted()).as("interrupt flag restored").isTrue();
        assertThat(outcomes).containsOnlyKeys(CloudProvider.DIGITALOCEAN);
        assertThat(outcomes.get(CloudProvider.DIGITALOCEAN).error()).isEqualTo("interrupted");
    }

    @Test
    @DisplayName("syncAll over zero providers is an empty, honest report")
    void syncAllEmpty() {
        assertThat(new DigitalOceanClient().syncAll(List.of(), new InfraGraph(), p -> {
        })).isEmpty();
    }

    // ---- no-token refusals: sync fails fast, before any network ----

    @Test
    @DisplayName("sync without a DigitalOcean token refuses by name, before any network")
    void syncWithoutDoToken() {
        assumeNoToken(CloudProvider.DIGITALOCEAN);
        assertThatThrownBy(() -> new DigitalOceanClient()
                .sync(CloudProvider.DIGITALOCEAN, new InfraGraph()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no DigitalOcean API token");
    }

    @Test
    @DisplayName("sync without a Hetzner token refuses by name")
    void syncWithoutHetznerToken() {
        assumeNoToken(CloudProvider.HETZNER);
        assertThatThrownBy(() -> new DigitalOceanClient()
                .sync(CloudProvider.HETZNER, new InfraGraph()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no Hetzner Cloud API token");
    }

    @Test
    @DisplayName("sync without a Cloudflare token refuses by name")
    void syncWithoutCloudflareToken() {
        assumeNoToken(CloudProvider.CLOUDFLARE);
        assertThatThrownBy(() -> new DigitalOceanClient()
                .sync(CloudProvider.CLOUDFLARE, new InfraGraph()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no Cloudflare API token");
    }

    // ---- drift & destroy: honest statuses without a live cloud ----

    @Test
    @DisplayName("refreshDrift labels un-checkable nodes honestly and never throws over one failure")
    void refreshDriftHonestStatuses() throws Exception {
        assumeNoToken(CloudProvider.DIGITALOCEAN);
        InfraGraph graph = new InfraGraph();
        InfraNode designed = graph.addNode(NodeKind.DROPLET, 0, 0);      // no doId: skipped
        InfraNode noReadApi = graph.addNode(NodeKind.CF_DNS_RECORD, 0, 100);
        noReadApi.doId = "rec-1";                                        // no zoneId: no path
        InfraNode unreachable = graph.addNode(NodeKind.DROPLET, 0, 200);
        unreachable.doId = "9";                                          // send refuses: no token

        Map<String, String> statuses = new java.util.LinkedHashMap<>();
        new DigitalOceanClient().refreshDrift(graph,
                (node, status) -> statuses.put(node.id, status));

        assertThat(statuses).doesNotContainKey(designed.id);
        assertThat(statuses.get(noReadApi.id)).isEqualTo("unverifiable (no read API)");
        assertThat(statuses.get(unreachable.id)).startsWith("check failed:")
                .contains("no DigitalOcean API token");
        assertThat(unreachable.doId).as("only a real HTTP 404 severs linkage").isEqualTo("9");
    }

    @Test
    @DisplayName("destroyAll: no-id nodes pass, path-less kinds refuse honestly, failures are counted")
    void destroyAllHonestOutcomes() {
        assumeNoToken(CloudProvider.DIGITALOCEAN);
        InfraGraph graph = new InfraGraph();
        InfraNode neverDeployed = graph.addNode(NodeKind.DROPLET, 0, 0); // doId null
        InfraNode noDeleteApi = graph.addNode(NodeKind.CF_DNS_RECORD, 0, 100);
        noDeleteApi.doId = "rec-2";                                      // zone-less: path null
        InfraNode failing = graph.addNode(NodeKind.DROPLET, 0, 200);
        failing.doId = "42";                                             // send refuses: no token

        Map<String, String> last = new java.util.LinkedHashMap<>();
        int failures = new DigitalOceanClient().destroyAll(
                List.of(neverDeployed, noDeleteApi, failing),
                (node, msg) -> last.put(node.id, msg));

        assertThat(failures).isEqualTo(2);
        assertThat(last.get(neverDeployed.id)).isEqualTo("destroyed");
        assertThat(last.get(noDeleteApi.id)).isEqualTo("no delete API — remove manually");
        assertThat(last.get(failing.id)).startsWith("destroy failed:");
        assertThat(noDeleteApi.doId).as("an undeletable resource keeps its id").isEqualTo("rec-2");
    }

    // ---- onModel: the EDT-marshalling seam (ledger 53d) ----

    @Test
    @DisplayName("onModel runs directly when already on the EDT")
    void onModelOnEdtRunsInline() throws Exception {
        AtomicBoolean ranOnEdt = new AtomicBoolean();
        javax.swing.SwingUtilities.invokeAndWait(() ->
                DigitalOceanClient.onModel(() ->
                        ranOnEdt.set(javax.swing.SwingUtilities.isEventDispatchThread())));
        assertThat(ranOnEdt).isTrue();
    }

    @Test
    @DisplayName("a mutation's own failure surfaces as its cause, not a reflection wrapper")
    void onModelUnwrapsMutationFailure() {
        assertThatThrownBy(() -> DigitalOceanClient.onModel(() -> {
            throw new IllegalStateException("model boom");
        })).isInstanceOf(RuntimeException.class)
                .cause().isInstanceOf(IllegalStateException.class)
                .hasMessage("model boom");
    }

    @Test
    @DisplayName("an interrupted caller keeps its interrupt flag")
    void onModelInterruptedKeepsFlag() {
        Thread.currentThread().interrupt();
        try {
            DigitalOceanClient.onModel(() -> {
            });
            assertThat(Thread.interrupted()).as("interrupt restored, not swallowed").isTrue();
        } finally {
            Thread.interrupted(); // leave the thread clean whatever happened
        }
    }

    // ---- parse edges the sync fixtures didn't reach ----

    @Test
    @DisplayName("extractId yields null for kinds whose creation response carries no id")
    void extractIdDefaultKind() {
        assertThat(DigitalOceanClient.extractId(NodeKind.SPACES, new JSONObject())).isNull();
    }

    @Test
    @DisplayName("parseListResponse skips malformed items and labels an app without a spec by id")
    void parseListResponseEdges() {
        var droplets = new DigitalOceanClient.Source(
                "/v2/droplets", "droplets", NodeKind.DROPLET, "id", "name");
        String json = """
                {"droplets": [
                  "not-an-object",
                  {"name": "no-id-here"},
                  {"id": 7, "name": "web",
                   "networks": {"v4": [
                     {"type": "private", "ip_address": "10.0.0.2"},
                     {"type": "public", "ip_address": "203.0.113.9"}]}},
                  {"id": 8, "name": "netless"}
                ]}""";
        var imported = DigitalOceanClient.parseListResponse(droplets, json);
        assertThat(imported).hasSize(2);
        assertThat(imported.get(0).ip()).isEqualTo("203.0.113.9");
        assertThat(imported.get(1).ip()).isNull();

        var apps = new DigitalOceanClient.Source(
                "/v2/apps", "apps", NodeKind.APP_PLATFORM, "id", "spec");
        var specless = DigitalOceanClient.parseListResponse(
                apps, """
                      {"apps": [{"id": "a-1"}]}""");
        assertThat(specless).hasSize(1);
        assertThat(specless.get(0).label()).isEqualTo("a-1");
    }

    @Test
    @DisplayName("a Hetzner server's public_net IPv4 imports; an empty one reads as none")
    void parseHetznerServerIp() {
        var servers = new DigitalOceanClient.Source(
                "/servers", "servers", NodeKind.HZ_SERVER, "id", "name");
        var imported = DigitalOceanClient.parseListResponse(servers, """
                {"servers": [
                  {"id": 1, "name": "app", "public_net": {"ipv4": {"ip": "198.51.100.4"}}},
                  {"id": 2, "name": "dark", "public_net": {"ipv4": {"ip": ""}}}
                ]}""");
        assertThat(imported).extracting(DigitalOceanClient.Imported::ip)
                .containsExactly("198.51.100.4", null);
    }
}

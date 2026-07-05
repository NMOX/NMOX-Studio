package org.nmox.studio.web3.engine;

import java.io.File;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.service.ServingRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ANVIL auto-connect: endpoint matching tolerances, the pure
 * transition table, and the storm law on the live listener — N
 * register/deregister flaps produce at most one chip reaction per
 * actual state change, and a detached (tab closed) studio reacts to
 * nothing.
 */
class ChainAutoConnectTest {

    private static final String ANVIL = "http://127.0.0.1:8545";

    // ---- endpoint matching -------------------------------------------------

    @Test
    @DisplayName("localhost and 127.0.0.1 and 0.0.0.0 front the same endpoint")
    void loopbackSpellingsMatch() {
        assertThat(ChainAutoConnect.sameEndpoint(
                "http://127.0.0.1:8545", "http://localhost:8545")).isTrue();
        assertThat(ChainAutoConnect.sameEndpoint(
                "http://0.0.0.0:8545", "http://localhost:8545")).isTrue();
        assertThat(ChainAutoConnect.sameEndpoint(
                "http://[::1]:8545", "http://127.0.0.1:8545")).isTrue();
    }

    @Test
    @DisplayName("a scheme-less URL and a trailing path still match host+port")
    void schemeAndPathTolerance() {
        assertThat(ChainAutoConnect.sameEndpoint(
                "127.0.0.1:8545", "http://localhost:8545")).isTrue();
        assertThat(ChainAutoConnect.sameEndpoint(
                "http://localhost:8545/rpc", "http://127.0.0.1:8545")).isTrue();
    }

    @Test
    @DisplayName("different ports or hosts do not match; defaults fill missing ports")
    void mismatches() {
        assertThat(ChainAutoConnect.sameEndpoint(
                "http://127.0.0.1:8545", "http://127.0.0.1:8546")).isFalse();
        assertThat(ChainAutoConnect.sameEndpoint(
                "http://10.0.0.5:8545", "http://localhost:8545")).isFalse();
        assertThat(ChainAutoConnect.sameEndpoint(
                "http://example.com", "http://example.com:80")).isTrue();
        assertThat(ChainAutoConnect.sameEndpoint(
                "https://example.com", "http://example.com")).isFalse();
    }

    @Test
    @DisplayName("garbage and blanks match nothing — not even themselves")
    void garbage() {
        assertThat(ChainAutoConnect.sameEndpoint("not a url at all", "not a url at all"))
                .isFalse();
        assertThat(ChainAutoConnect.sameEndpoint(null, ANVIL)).isFalse();
        assertThat(ChainAutoConnect.sameEndpoint("", ANVIL)).isFalse();
        assertThat(ChainAutoConnect.endpointKey("http://:8545")).isNull();
    }

    // ---- the pure transition table ------------------------------------------

    @Test
    @DisplayName("serving appears while not connected: CONNECT, once")
    void appearsConnects() {
        ChainAutoConnect.Decision first = ChainAutoConnect.decide(
                ANVIL, List.of(ANVIL), null, false);
        assertThat(first.action()).isEqualTo(ChainAutoConnect.Action.CONNECT);
        assertThat(first.matchedUrl()).isEqualTo(ANVIL);

        // the next notification with the serving still there is a no-op,
        // whether the connect landed or is still probing
        assertThat(ChainAutoConnect.decide(ANVIL, List.of(ANVIL), ANVIL, true).action())
                .isEqualTo(ChainAutoConnect.Action.NONE);
        assertThat(ChainAutoConnect.decide(ANVIL, List.of(ANVIL), ANVIL, false).action())
                .isEqualTo(ChainAutoConnect.Action.NONE);
    }

    @Test
    @DisplayName("serving appears while already connected: nothing to do")
    void appearsWhileConnected() {
        ChainAutoConnect.Decision d = ChainAutoConnect.decide(
                ANVIL, List.of(ANVIL), null, true);
        assertThat(d.action()).isEqualTo(ChainAutoConnect.Action.NONE);
        assertThat(d.matchedUrl()).isEqualTo(ANVIL); // still tracked for the disappearance
    }

    @Test
    @DisplayName("serving disappears while connected: DISCONNECT; while grey: nothing")
    void disappears() {
        assertThat(ChainAutoConnect.decide(ANVIL, List.of(), ANVIL, true).action())
                .isEqualTo(ChainAutoConnect.Action.DISCONNECT);
        ChainAutoConnect.Decision grey = ChainAutoConnect.decide(
                ANVIL, List.of(), ANVIL, false);
        assertThat(grey.action()).isEqualTo(ChainAutoConnect.Action.NONE);
        assertThat(grey.matchedUrl()).isNull(); // state clears either way
    }

    @Test
    @DisplayName("switching to another network never inherits the old match")
    void networkSwitchResetsBaseline() {
        // user was on Local (matched), switched to a remote network,
        // connected there; anvil still runs — its serving must not grey
        // the remote chip when the registry next fires
        ChainAutoConnect.Decision d = ChainAutoConnect.decide(
                "https://rpc.example.com", List.of(ANVIL), ANVIL, true);
        assertThat(d.action()).isEqualTo(ChainAutoConnect.Action.NONE);
        assertThat(d.matchedUrl()).isNull();
    }

    @Test
    @DisplayName("no selected URL (secret network, empty combo): always NONE")
    void secretNetworksAreLeftAlone() {
        ChainAutoConnect.Decision d = ChainAutoConnect.decide(
                null, List.of(ANVIL), ANVIL, true);
        assertThat(d.action()).isEqualTo(ChainAutoConnect.Action.NONE);
        assertThat(d.matchedUrl()).isNull();
    }

    // ---- the live listener: bounded reactions --------------------------------

    /** Counts the studio calls; connect flips connected like a good probe. */
    private static final class FakeChain implements ChainAutoConnect.Chain {

        volatile String selectedUrl = "http://localhost:8545";
        volatile boolean connected;
        volatile boolean connectSucceeds = true;
        int connects;
        int disconnects;

        @Override
        public String selectedUrl() {
            return selectedUrl;
        }

        @Override
        public boolean connected() {
            return connected;
        }

        @Override
        public void connect() {
            connects++;
            if (connectSucceeds) {
                connected = true;
            }
        }

        @Override
        public void disconnect() {
            disconnects++;
            connected = false;
        }
    }

    private static ServingRegistry.Serving chain(String id, String url) {
        return new ServingRegistry.Serving(id, "ANVIL", url,
                ServingRegistry.Kind.CHAIN, new File("proj"));
    }

    private static ServingRegistry.Serving web(String id, String url) {
        return new ServingRegistry.Serving(id, "DEV-SERVER", url,
                ServingRegistry.Kind.WEB, new File("proj"));
    }

    /** Drains the registry notifier, then the EDT — the two async hops. */
    private static void settle(ServingRegistry registry) throws Exception {
        registry.awaitIdle();
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    @Test
    @DisplayName("N flaps: exactly one connect per appearance, one disconnect per loss")
    void flapsAreBounded() throws Exception {
        ServingRegistry registry = new ServingRegistry();
        FakeChain studio = new FakeChain();
        ChainAutoConnect auto = new ChainAutoConnect(registry, studio);
        auto.attach();
        try {
            for (int i = 0; i < 5; i++) {
                registry.register(chain("anvil", ANVIL));
                settle(registry);
                registry.deregister("anvil");
                settle(registry);
            }
            assertThat(studio.connects).isEqualTo(5);
            assertThat(studio.disconnects).isEqualTo(5);
        } finally {
            auto.detach();
        }
    }

    @Test
    @DisplayName("unrelated registry churn while connected causes no chip traffic")
    void unrelatedChurnIsIgnored() throws Exception {
        ServingRegistry registry = new ServingRegistry();
        FakeChain studio = new FakeChain();
        ChainAutoConnect auto = new ChainAutoConnect(registry, studio);
        auto.attach();
        try {
            registry.register(chain("anvil", ANVIL));
            settle(registry);
            assertThat(studio.connects).isEqualTo(1);

            for (int i = 0; i < 10; i++) {
                registry.register(web("vite", "http://localhost:517" + (i % 2)));
                settle(registry);
            }
            registry.deregister("vite");
            settle(registry);

            assertThat(studio.connects).isEqualTo(1);
            assertThat(studio.disconnects).isZero();
        } finally {
            auto.detach();
        }
    }

    @Test
    @DisplayName("a connect that keeps failing is retried once per appearance, not per event")
    void failingConnectDoesNotStorm() throws Exception {
        ServingRegistry registry = new ServingRegistry();
        FakeChain studio = new FakeChain();
        studio.connectSucceeds = false; // the probe never lands
        ChainAutoConnect auto = new ChainAutoConnect(registry, studio);
        auto.attach();
        try {
            registry.register(chain("anvil", ANVIL));
            settle(registry);
            for (int i = 0; i < 10; i++) {
                registry.register(web("vite", "http://localhost:517" + (i % 2)));
                settle(registry);
            }
            assertThat(studio.connects).isEqualTo(1); // not 11

            registry.deregister("anvil");
            settle(registry);
            assertThat(studio.disconnects).isZero(); // chip was never green

            registry.register(chain("anvil", ANVIL));
            settle(registry);
            assertThat(studio.connects).isEqualTo(2); // a real re-appearance
        } finally {
            auto.detach();
        }
    }

    @Test
    @DisplayName("never attached or detached (tab closed): flaps reach nothing")
    void closedStudioReactsToNothing() throws Exception {
        ServingRegistry registry = new ServingRegistry();
        FakeChain studio = new FakeChain();
        ChainAutoConnect auto = new ChainAutoConnect(registry, studio);

        // never attached
        registry.register(chain("anvil", ANVIL));
        settle(registry);
        assertThat(studio.connects).isZero();

        // attached, then closed
        auto.attach();
        auto.detach();
        registry.deregister("anvil");
        registry.register(chain("anvil", ANVIL));
        settle(registry);
        assertThat(studio.connects).isZero();
        assertThat(studio.disconnects).isZero();
    }

    @Test
    @DisplayName("refresh() connects to a chain that was already up at tab-open")
    void refreshCatchesExistingServing() throws Exception {
        ServingRegistry registry = new ServingRegistry();
        registry.register(chain("anvil", ANVIL));
        registry.awaitIdle();

        FakeChain studio = new FakeChain();
        ChainAutoConnect auto = new ChainAutoConnect(registry, studio);
        auto.attach();
        try {
            auto.refresh();
            settle(registry);
            assertThat(studio.connects).isEqualTo(1);

            auto.refresh(); // idempotent: still present, now connected
            settle(registry);
            assertThat(studio.connects).isEqualTo(1);
        } finally {
            auto.detach();
        }
    }

    @Test
    @DisplayName("attach twice, detach once: no duplicate listener survives")
    void attachIsIdempotent() throws Exception {
        ServingRegistry registry = new ServingRegistry();
        FakeChain studio = new FakeChain();
        ChainAutoConnect auto = new ChainAutoConnect(registry, studio);
        auto.attach();
        auto.attach();
        auto.detach();
        registry.register(chain("anvil", ANVIL));
        settle(registry);
        assertThat(studio.connects).isZero();
    }
}

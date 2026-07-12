package org.nmox.studio.apiclient.api;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel.Environment;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;
import org.nmox.studio.core.spi.LiveServings;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The registry→EDT bridge: lifecycle discipline (closed studios react
 * to nothing, open/close cycles never stack listeners) and the storm
 * law end-to-end — registry flaps produce at most ONE offer through the
 * session guard.
 */
class ServingBridgeTest {

    private static final File PROJECT = new File("/tmp/shop").getAbsoluteFile();

    private static LiveServings.Serving web(String id, String url) {
        return new LiveServings.Serving(id, "DEV-SERVER", url,
                LiveServings.Kind.WEB, PROJECT);
    }

    /** Drains the registry notifier, then the EDT — the two async hops. */
    private static void settle(FakeLiveServings registry) throws Exception {
        registry.awaitIdle();
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    @Test
    @DisplayName("attached: every registry change delivers a fresh snapshot on the EDT")
    void deliversSnapshots() throws Exception {
        FakeLiveServings registry = new FakeLiveServings();
        List<List<LiveServings.Serving>> seen = new ArrayList<>();
        List<Boolean> onEdt = new ArrayList<>();
        ServingBridge bridge = new ServingBridge(registry, snapshot -> {
            seen.add(snapshot);
            onEdt.add(SwingUtilities.isEventDispatchThread());
        });
        bridge.attach();
        try {
            registry.register(web("dev", "http://localhost:5173"));
            settle(registry);
            registry.deregister("dev");
            settle(registry);

            assertThat(seen).hasSize(2);
            assertThat(seen.get(0)).extracting(LiveServings.Serving::url)
                    .containsExactly("http://localhost:5173");
            assertThat(seen.get(1)).isEmpty();
            assertThat(onEdt).containsOnly(true);
        } finally {
            bridge.detach();
        }
    }

    @Test
    @DisplayName("never attached or detached: registry flaps reach nothing")
    void closedStudioHearsNothing() throws Exception {
        FakeLiveServings registry = new FakeLiveServings();
        List<List<LiveServings.Serving>> seen = new ArrayList<>();
        ServingBridge bridge = new ServingBridge(registry, seen::add);

        registry.register(web("dev", "http://localhost:5173"));
        settle(registry);
        assertThat(seen).isEmpty(); // never attached

        bridge.attach();
        bridge.attach(); // idempotent — one listener, not two
        bridge.detach();
        registry.deregister("dev");
        registry.register(web("dev", "http://localhost:5173"));
        settle(registry);
        assertThat(seen).isEmpty(); // detached before the flaps
    }

    @Test
    @DisplayName("refresh() delivers the current snapshot without a registry event")
    void refreshDelivers() throws Exception {
        FakeLiveServings registry = new FakeLiveServings();
        registry.register(web("dev", "http://localhost:5173"));
        registry.awaitIdle();

        List<List<LiveServings.Serving>> seen = new ArrayList<>();
        ServingBridge bridge = new ServingBridge(registry, seen::add);
        bridge.attach();
        try {
            bridge.refresh();
            SwingUtilities.invokeAndWait(() -> {
            });
            assertThat(seen).hasSize(1);
            assertThat(seen.get(0)).hasSize(1);
        } finally {
            bridge.detach();
        }
    }

    @Test
    @DisplayName("storm law: N register/deregister flaps still yield exactly one offer")
    void flapsYieldOneOffer() throws Exception {
        FakeLiveServings registry = new FakeLiveServings();
        Workspace workspace = new Workspace();
        Environment env = new Environment();
        env.name = "Dev";
        workspace.environments.add(env);
        workspace.activeEnvironment = "Dev";

        Set<String> offered = new HashSet<>();
        List<BaseUrlOffer.Offer> offers = new ArrayList<>();
        ServingBridge bridge = new ServingBridge(registry, snapshot -> {
            BaseUrlOffer.Offer offer = BaseUrlOffer.shouldOffer(
                    snapshot, PROJECT, workspace, offered);
            if (offer != null) {
                offered.add(offer.guardKey()); // what the studio does at offer time
                offers.add(offer);
            }
        });
        bridge.attach();
        try {
            for (int i = 0; i < 10; i++) {
                registry.register(web("dev", "http://localhost:5173"));
                settle(registry);
                registry.deregister("dev");
                settle(registry);
            }
            assertThat(offers).hasSize(1); // ten flaps, one balloon
        } finally {
            bridge.detach();
        }
    }
}

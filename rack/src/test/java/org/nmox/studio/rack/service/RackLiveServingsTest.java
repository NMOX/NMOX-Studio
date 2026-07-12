package org.nmox.studio.rack.service;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.LiveServings;
import org.openide.util.Lookup;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@link LiveServings} facade contract (ledger 30): the adapter is
 * a pure delegate over {@link ServingRegistry} — snapshots convert
 * record-for-record in registration order, and the wrapped listener
 * shape stays symmetric (add/remove operate on the same wrapper; a
 * double-add never double-delivers).
 */
class RackLiveServingsTest {

    private static final File PROJECT = new File("/tmp/shop").getAbsoluteFile();

    @Test
    @DisplayName("the rack module publishes the provider — studios find it by lookup")
    void lookupFindsProvider() {
        LiveServings servings = Lookup.getDefault().lookup(LiveServings.class);
        assertThat(servings).isInstanceOf(RackLiveServings.class);
    }

    @Test
    @DisplayName("snapshot converts every field and keeps registration order")
    void snapshotConverts() {
        ServingRegistry registry = new ServingRegistry();
        LiveServings servings = new RackLiveServings(registry);

        registry.register(new ServingRegistry.Serving("dev", "DEV-SERVER",
                "http://localhost:5173", ServingRegistry.Kind.WEB, PROJECT));
        registry.register(new ServingRegistry.Serving("anvil", "ANVIL",
                "http://127.0.0.1:8545", ServingRegistry.Kind.CHAIN, PROJECT));

        List<LiveServings.Serving> snapshot = servings.snapshot();
        assertThat(snapshot).containsExactly(
                new LiveServings.Serving("dev", "DEV-SERVER",
                        "http://localhost:5173", LiveServings.Kind.WEB, PROJECT),
                new LiveServings.Serving("anvil", "ANVIL",
                        "http://127.0.0.1:8545", LiveServings.Kind.CHAIN, PROJECT));
    }

    @Test
    @DisplayName("every registry Kind has a facade counterpart (a new one must be mirrored, not mis-mapped)")
    void kindsMirrorTheRegistry() {
        for (ServingRegistry.Kind kind : ServingRegistry.Kind.values()) {
            // valueOf throws if the facade enum ever falls behind
            assertThat(LiveServings.Kind.valueOf(kind.name()).name())
                    .isEqualTo(kind.name());
        }
    }

    @Test
    @DisplayName("listener lifecycle: subscribed hears each change once; removed hears nothing; double-add never double-delivers")
    void listenerLifecycle() {
        ServingRegistry registry = new ServingRegistry();
        LiveServings servings = new RackLiveServings(registry);
        AtomicInteger heard = new AtomicInteger();
        LiveServings.Listener listener = heard::incrementAndGet;

        servings.addListener(listener);
        servings.addListener(listener); // must not double-deliver
        registry.register(new ServingRegistry.Serving("dev", "DEV-SERVER",
                "http://localhost:5173", ServingRegistry.Kind.WEB, PROJECT));
        registry.awaitIdle();
        assertThat(heard.get()).isEqualTo(1);

        servings.removeListener(listener);
        registry.deregister("dev");
        registry.awaitIdle();
        assertThat(heard.get()).isEqualTo(1); // detached: silence

        // removing again (or a stranger) is a quiet no-op
        servings.removeListener(listener);
        servings.removeListener(() -> { });
    }
}

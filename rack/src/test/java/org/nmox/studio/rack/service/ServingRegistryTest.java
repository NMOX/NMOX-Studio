package org.nmox.studio.rack.service;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.service.ServingRegistry.Kind;
import org.nmox.studio.rack.service.ServingRegistry.Serving;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The serving registry's contract: coarse events, snapshot re-reads,
 * and the idempotence law — equal re-registration and absent
 * deregistration fire NOTHING, because a server re-printing its URL
 * must not ripple through every consumer in the IDE.
 */
class ServingRegistryTest {

    private final File project = new File("/tmp/serving-registry-test");

    private static Serving serving(String id, String url) {
        return new Serving(id, "SURGE", url,
                Kind.WEB, new File("/tmp/serving-registry-test"));
    }

    @Test
    @DisplayName("register appears in the snapshot; deregister removes it")
    void registerAndDeregister() {
        ServingRegistry registry = new ServingRegistry();
        registry.register(serving("a", "http://localhost:5173"));
        assertThat(registry.snapshot())
                .extracting(Serving::url).containsExactly("http://localhost:5173");

        registry.deregister("a");
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    @DisplayName("snapshot preserves registration order — 'first serving' is stable")
    void snapshotOrder() {
        ServingRegistry registry = new ServingRegistry();
        registry.register(serving("a", "http://localhost:1111"));
        registry.register(serving("b", "http://localhost:2222"));
        registry.register(serving("c", "http://localhost:3333"));
        assertThat(registry.snapshot()).extracting(Serving::url).containsExactly(
                "http://localhost:1111", "http://localhost:2222", "http://localhost:3333");
    }

    @Test
    @DisplayName("STORM LAW: re-registering an equal serving fires no event")
    void equalRegisterFiresNothing() {
        ServingRegistry registry = new ServingRegistry();
        AtomicInteger fired = new AtomicInteger();
        registry.register(serving("a", "http://localhost:5173"));
        registry.awaitIdle();
        registry.addListener(fired::incrementAndGet);

        // the same URL announced again — a server re-printing its banner
        registry.register(serving("a", "http://localhost:5173"));
        registry.awaitIdle();
        assertThat(fired).as("equal re-register is silent").hasValue(0);

        // a DIFFERENT url for the same device replaces and fires once
        registry.register(serving("a", "http://localhost:5174"));
        registry.awaitIdle();
        assertThat(fired).hasValue(1);
        assertThat(registry.snapshot())
                .extracting(Serving::url).containsExactly("http://localhost:5174");
    }

    @Test
    @DisplayName("deregistering an absent id fires no event")
    void absentDeregisterFiresNothing() {
        ServingRegistry registry = new ServingRegistry();
        AtomicInteger fired = new AtomicInteger();
        registry.addListener(fired::incrementAndGet);
        registry.deregister("never-registered");
        registry.awaitIdle();
        assertThat(fired).hasValue(0);
    }

    @Test
    @DisplayName("events are delivered on the registry's own thread, never the caller's")
    void eventsOffCallerThread() {
        ServingRegistry registry = new ServingRegistry();
        AtomicReference<Thread> deliveredOn = new AtomicReference<>();
        registry.addListener(() -> deliveredOn.set(Thread.currentThread()));
        registry.register(serving("a", "http://localhost:5173"));
        registry.awaitIdle();
        assertThat(deliveredOn.get()).isNotNull();
        assertThat(deliveredOn.get()).isNotSameAs(Thread.currentThread());
        assertThat(javax.swing.SwingUtilities.isEventDispatchThread()).isFalse();
        assertThat(deliveredOn.get().getName()).isEqualTo("nmox-serving-registry");
    }

    @Test
    @DisplayName("a failing listener does not starve the others")
    void listenerFailureIsolated() {
        ServingRegistry registry = new ServingRegistry();
        AtomicInteger healthy = new AtomicInteger();
        registry.addListener(() -> {
            throw new IllegalStateException("boom");
        });
        registry.addListener(healthy::incrementAndGet);
        registry.register(serving("a", "http://localhost:5173"));
        registry.awaitIdle();
        assertThat(healthy).hasValue(1);
    }

    @Test
    @DisplayName("getDefault is a stable singleton across lookups")
    void getDefaultStable() {
        assertThat(ServingRegistry.getDefault()).isSameAs(ServingRegistry.getDefault());
    }

    @Test
    @DisplayName("removed listeners hear nothing further")
    void removeListener() {
        ServingRegistry registry = new ServingRegistry();
        AtomicInteger fired = new AtomicInteger();
        ServingRegistry.Listener listener = fired::incrementAndGet;
        registry.addListener(listener);
        registry.register(serving("a", "http://localhost:5173"));
        registry.awaitIdle();
        registry.removeListener(listener);
        registry.register(serving("b", "http://localhost:5174"));
        registry.awaitIdle();
        assertThat(fired).hasValue(1);
    }

    @Test
    @DisplayName("Serving record carries the fields consumers filter on")
    void servingRecord() {
        Serving s = new Serving("dev@1", "SURGE", "http://localhost:5173", Kind.WEB, project);
        assertThat(s.deviceId()).isEqualTo("dev@1");
        assertThat(s.deviceTitle()).isEqualTo("SURGE");
        assertThat(s.kind()).isEqualTo(Kind.WEB);
        assertThat(s.projectDir()).isEqualTo(project);
        assertThat(List.of(Kind.values())).containsExactly(Kind.WEB, Kind.CHAIN);
    }
}

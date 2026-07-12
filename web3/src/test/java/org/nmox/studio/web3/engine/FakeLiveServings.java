package org.nmox.studio.web3.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.nmox.studio.core.spi.LiveServings;

/**
 * A test double with the REAL registry's threading contract: servings
 * registered/deregistered here notify listeners on a dedicated single
 * background thread (never the test thread, never the EDT), so the
 * bridge tests keep exercising both async hops exactly as they did
 * against the rack's ServingRegistry before the module went
 * lookup-soft (ledger 30).
 */
final class FakeLiveServings implements LiveServings {

    private final Map<String, Serving> servings = new LinkedHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService notifier = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fake-live-servings");
        t.setDaemon(true);
        return t;
    });

    void register(Serving serving) {
        synchronized (servings) {
            Serving previous = servings.put(serving.deviceId(), serving);
            if (serving.equals(previous)) {
                return; // equality-guarded, like the real registry
            }
        }
        fireChanged();
    }

    void deregister(String deviceId) {
        synchronized (servings) {
            if (servings.remove(deviceId) == null) {
                return;
            }
        }
        fireChanged();
    }

    @Override
    public List<Serving> snapshot() {
        synchronized (servings) {
            return new ArrayList<>(servings.values());
        }
    }

    @Override
    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void fireChanged() {
        notifier.submit(() -> listeners.forEach(Listener::servingChanged));
    }

    /** Blocks until every queued notification has been delivered. */
    void awaitIdle() throws Exception {
        notifier.submit(() -> { }).get(10, TimeUnit.SECONDS);
    }
}

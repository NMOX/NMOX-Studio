package org.nmox.studio.rack.service;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Who is serving what, live: the one registry every "this device is
 * serving a URL right now" fact passes through. Serve devices register
 * the moment they announce READY/URL (the same moment they emit the URL
 * signal) and deregister when the process exits — so the status line,
 * Quick Search, and the quality gates can answer "is anything up?"
 * without polling ports or re-parsing stdout.
 *
 * <p>Listeners are deliberately coarse: {@code servingChanged()} carries
 * no payload, consumers re-read {@link #snapshot()} — idempotent by
 * construction, nothing to mis-handle. Notifications are delivered on
 * the registry's own single background thread (never the caller's
 * thread, never the EDT); consumers marshal to the EDT themselves.
 */
@ServiceProvider(service = ServingRegistry.class)
public class ServingRegistry {

    /** What kind of thing the URL fronts. */
    public enum Kind { WEB, CHAIN }

    /** One live serving: a device, its URL, and the project it serves. */
    public record Serving(String deviceId, String deviceTitle, String url,
            Kind kind, File projectDir) {
    }

    /** Coarse change notification; re-read {@link #snapshot()}. */
    public interface Listener {
        void servingChanged();
    }

    public static ServingRegistry getDefault() {
        ServingRegistry registry = Lookup.getDefault().lookup(ServingRegistry.class);
        return registry != null ? registry : Holder.FALLBACK;
    }

    /** Outside the platform (plain unit tests) Lookup may be empty. */
    private static final class Holder {
        static final ServingRegistry FALLBACK = new ServingRegistry();
    }

    /** Insertion-ordered so "the first serving" is stable for consumers. */
    private final Map<String, Serving> servings = new LinkedHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService notifier = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nmox-serving-registry");
        t.setDaemon(true);
        return t;
    });

    /**
     * Registers (or replaces) the serving for its device id. Registering
     * a serving equal to the one already present fires NO event — a
     * server re-printing its own URL must not ripple through the IDE.
     */
    public void register(Serving serving) {
        if (serving == null) {
            return;
        }
        synchronized (servings) {
            Serving previous = servings.put(serving.deviceId(), serving);
            if (serving.equals(previous)) {
                return;
            }
        }
        fireChanged();
    }

    /** Removes a device's serving; absent ids fire NO event. */
    public void deregister(String deviceId) {
        synchronized (servings) {
            if (servings.remove(deviceId) == null) {
                return;
            }
        }
        fireChanged();
    }

    /** All current servings, registration-ordered. */
    public List<Serving> snapshot() {
        synchronized (servings) {
            return new ArrayList<>(servings.values());
        }
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fireChanged() {
        notifier.submit(() -> {
            for (Listener l : listeners) {
                try {
                    l.servingChanged();
                } catch (RuntimeException ex) {
                    java.util.logging.Logger.getLogger(ServingRegistry.class.getName())
                            .warning("Serving listener failed: " + ex);
                }
            }
        });
    }

    /**
     * Blocks until every notification queued before this call has been
     * delivered — mirrors {@code Rack.awaitRouterIdle} so tests can drain
     * the notifier instead of racing it. Test/diagnostic support.
     */
    public void awaitIdle() {
        try {
            notifier.submit(() -> { }).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new IllegalStateException("serving registry notifier did not drain", ex);
        }
    }
}

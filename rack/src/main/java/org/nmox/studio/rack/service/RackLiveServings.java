package org.nmox.studio.rack.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.nmox.studio.core.spi.LiveServings;
import org.openide.util.lookup.ServiceProvider;

/**
 * The rack's {@link LiveServings} provider: a thin adapter over
 * {@link ServingRegistry} so read-only consumers (API Studio's baseUrl
 * offer, Contract Studio's chain auto-connect) need no rack classes
 * (ledger 30). Notifications keep the registry's own single background
 * thread — the wrapper forwards on whatever thread the registry calls.
 */
@ServiceProvider(service = LiveServings.class)
public final class RackLiveServings implements LiveServings {

    private final ServingRegistry registry;
    /** Wrapper per subscribed listener; guarded by itself. */
    private final Map<Listener, ServingRegistry.Listener> wrappers = new LinkedHashMap<>();

    public RackLiveServings() {
        this(ServingRegistry.getDefault());
    }

    /** Test seam: adapt an isolated registry instead of the singleton. */
    RackLiveServings(ServingRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<Serving> snapshot() {
        List<ServingRegistry.Serving> source = registry.snapshot();
        List<Serving> result = new ArrayList<>(source.size());
        for (ServingRegistry.Serving serving : source) {
            result.add(convert(serving));
        }
        return result;
    }

    private static Serving convert(ServingRegistry.Serving serving) {
        // by-name so a Kind added to the registry without a facade
        // counterpart fails loudly here, not as a silent mis-map
        return new Serving(serving.deviceId(), serving.deviceTitle(),
                serving.url(), Kind.valueOf(serving.kind().name()),
                serving.projectDir());
    }

    @Override
    public void addListener(Listener listener) {
        ServingRegistry.Listener wrapper;
        synchronized (wrappers) {
            if (wrappers.containsKey(listener)) {
                return; // already subscribed: never double-deliver
            }
            wrapper = listener::servingChanged;
            wrappers.put(listener, wrapper);
        }
        registry.addListener(wrapper);
    }

    @Override
    public void removeListener(Listener listener) {
        ServingRegistry.Listener wrapper;
        synchronized (wrappers) {
            wrapper = wrappers.remove(listener);
        }
        if (wrapper != null) {
            registry.removeListener(wrapper);
        }
    }
}

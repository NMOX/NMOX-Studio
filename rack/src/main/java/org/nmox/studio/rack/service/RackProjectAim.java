package org.nmox.studio.rack.service;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.rack.model.Rack;
import org.openide.util.lookup.ServiceProvider;

/**
 * The rack's {@link ProjectAim} provider: a thin adapter over
 * {@link RackService} so studios can follow the aim without compiling
 * against rack (ledger 30). No logic lives here — every call delegates,
 * so threading, coalescing and the switch guard stay exactly
 * {@code RackService}'s.
 *
 * <p>Listener shape: {@link ProjectAim.Listener} is narrower than
 * {@link Rack.Listener}, so each subscription wraps once and the
 * wrapper is remembered — add/remove stay symmetric on the SAME
 * delegate instance, and a double-add is a no-op ({@code Rack}'s
 * CopyOnWriteArrayList would otherwise double-fire; consumers guard
 * with attached flags, the adapter honors that contract structurally).
 */
@ServiceProvider(service = ProjectAim.class)
public final class RackProjectAim implements ProjectAim {

    private final RackService service;
    /** Wrapper per subscribed listener; guarded by itself. */
    private final Map<Listener, Rack.Listener> wrappers = new LinkedHashMap<>();

    public RackProjectAim() {
        this(RackService.getDefault());
    }

    /** Test seam: adapt an isolated service instead of the singleton. */
    RackProjectAim(RackService service) {
        this.service = service;
    }

    @Override
    public File projectDir() {
        return service.getRack().getProjectDir();
    }

    @Override
    public void aim(File dir) {
        service.openProject(dir);
    }

    @Override
    public List<File> recentProjects() {
        return service.getRecentProjects();
    }

    @Override
    public void addListener(Listener listener) {
        Rack.Listener wrapper;
        synchronized (wrappers) {
            if (wrappers.containsKey(listener)) {
                return; // already subscribed: never double-deliver
            }
            wrapper = new Rack.Listener() {
                @Override
                public void projectChanged() {
                    listener.projectChanged();
                }
            };
            wrappers.put(listener, wrapper);
        }
        service.getRack().addListener(wrapper);
    }

    @Override
    public void removeListener(Listener listener) {
        Rack.Listener wrapper;
        synchronized (wrappers) {
            wrapper = wrappers.remove(listener);
        }
        if (wrapper != null) {
            service.getRack().removeListener(wrapper);
        }
    }

    @Override
    public void addManifestListener(Consumer<List<Path>> listener) {
        // same type on both sides: no wrapper, RackService's
        // CopyOnWriteArrayList semantics apply directly
        service.addManifestListener(listener);
    }

    @Override
    public void removeManifestListener(Consumer<List<Path>> listener) {
        service.removeManifestListener(listener);
    }
}
